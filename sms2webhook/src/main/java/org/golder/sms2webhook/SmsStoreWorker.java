package org.golder.sms2webhook;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.provider.Telephony;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.NoSuchAlgorithmException;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class SmsStoreWorker extends Worker {
    private static final String TAG = SmsStoreWorker.class.getSimpleName();

    /**
     * How many messages to process between full statistics refreshes.
     *
     * <p>A refresh re-queries the SMS provider and the cache, so doing it per
     * message made a sync spend most of its time in the database.
     */
    private static final int STATS_REFRESH_INTERVAL = 25;

    /**
     * Name the sync is enqueued under, which is what serialises runs.
     *
     * <p>Every incoming SMS enqueues a sync, and the user can start one from the
     * toolbar. Left as independent work requests, WorkManager ran them in parallel
     * and they both walked the same messages.
     */
    static final String UNIQUE_WORK_NAME = "sms-sync";

    /** Tag the work is also enqueued under, so it can be observed and cancelled. */
    static final String WORK_TAG = "message";

    private static final String PREF_WATERMARK = "watermark";

    private static final String PREF_WEBHOOK_URL = "webhook_url";
    private static final String PREF_API_KEY = "api_key";

    /**
     * Returned by {@link #uploadOrGiveUp} to mean "stop here and retry this
     * message later", rather than a status the server actually sent.
     */
    private static final int GIVE_UP = Integer.MIN_VALUE;

    private final Context context;

    public SmsStoreWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
        this.context = context;
    }

    /**
     * Rewinds the sync position so the next run rescans the inbox from the start.
     *
     * <p>Safe to call while a sync is running: that run keeps its own position and
     * writes it back on the way out, so the rewind is honoured by the run queued
     * behind it rather than corrupting the one in flight. Messages already dealt
     * with are recognised from the cache, so a rescan re-reads them rather than
     * re-sending them.
     */
    static void requestFullRescan(Context context) {
        writeWatermark(context, 0);
    }

    private static void writeWatermark(Context context, int watermark) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putInt(PREF_WATERMARK, watermark)
                .apply();
    }

    private void saveWatermark(int watermark) {
        writeWatermark(context, watermark);
    }

    public static JSONObject encodeMessage(Cursor cursor) {
        JSONObject jsonObject = new JSONObject();

        try {
            String[] columns = cursor.getColumnNames();
            for (String column : columns) {
                int idx = cursor.getColumnIndex(column);
                if (idx >= 0) {
                    jsonObject.put(column, cursor.getString(idx));
                }
            }
        } catch (SQLException | JSONException e) {
            Log.e(TAG, "Error parsing cursor to JSONObject", e);
        }

        return jsonObject;
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, "Working...");

        MainApplication app = (MainApplication) context.getApplicationContext();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String webhookUrl = prefs.getString(PREF_WEBHOOK_URL, "");
        if (webhookUrl.isEmpty()) {
            Log.e(TAG, "Webhook URL not defined.");
            app.addMessage("ERROR: Webhook URL not defined.");
            return Result.failure();
        }
        String apiKey = prefs.getString(PREF_API_KEY, "");

        // The sync position is local to this run and mirrored into preferences at
        // each exit point. It used to be a field on the shared Application object,
        // which overlapping workers both read and incremented, so they raced on the
        // position and each uploaded the same messages.
        int watermark = prefs.getInt(PREF_WATERMARK, 0);

        try (Cursor cursor = context.getContentResolver().query(
                Telephony.Sms.CONTENT_URI, null, null, null, "_id ASC")) {
            if (cursor == null) {
                Log.e(TAG, "Failed to query SMS inbox.");
                app.addMessage("ERROR: Failed to query SMS inbox.");
                return Result.failure();
            }
            int total = cursor.getCount();
            Log.i(TAG, "SMS message count: " + total);
            if (total == 0) {
                Log.i(TAG, "Empty SMS inbox.");
                app.addMessage("Empty SMS inbox.");
                // An empty inbox carries no position, so a message arriving later
                // would land at index 0 and sit behind a stale watermark, never
                // being uploaded.
                saveWatermark(0);
                app.updateStats();
                return Result.success();
            }

            if (watermark > total) {
                watermark = total;
            }

            while (watermark < total) {
                if (isStopped()) {
                    Log.i(TAG, "Worker stopped. Aborting sync.");
                    app.addMessage("Sync stopped by user.");
                    saveWatermark(watermark);
                    app.updateStats();
                    return Result.failure();
                }

                String ref = "[" + (watermark + 1) + " / " + total + "]";
                Log.i(TAG, app.getString(R.string.processing, ref));

                if (!cursor.moveToPosition(watermark)) {
                    Log.e(TAG, app.getString(R.string.unable_to_move_cursor_to_watermark_position, watermark));
                    app.addMessage(app.getString(R.string.unable_to_move_cursor_to_watermark_position, watermark));
                    saveWatermark(watermark);
                    app.updateStats();
                    // Retried, not failed. The position is still saved, so the retry
                    // picks up here rather than at the start, and returning failure
                    // meant nothing would come back: a deleted message could leave the
                    // cursor short of the saved index and wedge the sync permanently.
                    return Result.retry();
                }

                JSONObject json = encodeMessage(cursor);
                String hash;
                try {
                    // Over the message's identity, not the payload. The payload
                    // carries read/seen/status/_id, all of which change after
                    // delivery, so hashing it re-uploaded messages that had
                    // already been sent and could not recognise a message on a
                    // restored device.
                    hash = DigestUtil.getMessageDigest(json);
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }

                int cached = DigestCache.get(context, hash);
                if (SmsStoreWorkerStatusHandling.isTerminal(cached)) {
                    // Either already delivered or already refused for good. The
                    // server has seen this message either way, so sending it again
                    // would only duplicate it.
                    Log.i(TAG, "Terminal outcome " + cached + " already recorded for " + hash + ". Skipping.");
                    app.addMessage(ref + ": "
                            + app.getString(R.string.already_handled_with_status_code, cached));
                } else if (uploadOrGiveUp(app, ref, json, hash, webhookUrl, apiKey) == GIVE_UP) {
                    saveWatermark(watermark);
                    app.updateStats();
                    return Result.retry();
                }

                watermark++;

                // Report position every message so the bar tracks the sync. The
                // cached counts are only refreshed periodically, because
                // updateStats() re-queries the cache and the SMS provider and is
                // far too expensive to run per message.
                app.reportSyncProgress(watermark, total);
                if (watermark % STATS_REFRESH_INTERVAL == 0) {
                    // Counts only. Diagnostics would append a log entry on every
                    // tick, filling the activity log with the same finding.
                    app.refreshStats(false);
                }
            }

            saveWatermark(watermark);
            app.updateStats();

            return Result.success();
        }
    }

    /**
     * Uploads one message and records the outcome.
     *
     * <p>Every terminal outcome is written to the cache, and the sync skips
     * anything the cache reports as {@link SmsStoreWorkerStatusHandling#isTerminal}.
     * Writing it is therefore what stops a permanently rejected message being
     * re-sent on every later sync, which is exactly what a "skip only if 200"
     * check did: it recorded the rejection but never consulted it, so a rejected
     * message was re-uploaded on every run forever.
     *
     * @return the status the server returned, or {@link #GIVE_UP} when the sync
     *         should stop and retry without advancing past this message
     */
    private int uploadOrGiveUp(MainApplication app, String ref, JSONObject json, String hash,
                               String webhookUrl, String apiKey) {
        WebhookUploader uploader = new WebhookUploader(webhookUrl, apiKey);
        int statusCode;
        try {
            statusCode = uploader.upload(json);
        } catch (WebhookUploader.WebhookUploadException e) {
            // A transport failure says nothing about the message, only about the
            // connection. Treat it as transient and let WorkManager retry rather
            // than recording it, which would drop the message permanently.
            Log.e(TAG, "Upload exception: " + e.getMessage());
            app.addMessage(ref + ": Upload failed - " + e.getMessage() + " - will retry");
            return GIVE_UP;
        }

        if (SmsStoreWorkerStatusHandling.isSuccess(statusCode)) {
            // A duplicate is still a success, but saying "Uploaded" for it hides
            // that nothing was stored.
            int messageResId = uploader.wasAlreadyExisted()
                    ? R.string.already_existed_on_server
                    : R.string.uploaded_with_status_code;
            Log.i(TAG, app.getString(messageResId, statusCode));
            app.addMessage(ref + ": " + app.getString(messageResId, statusCode));
            DigestCache.set(context, hash, statusCode);
            return statusCode;
        }

        if (SmsStoreWorkerStatusHandling.abortsSync(statusCode)) {
            // A server fault says nothing about the message, only about the
            // connection. Stop, but ask WorkManager to come back rather than losing
            // what follows.
            Log.e(TAG, app.getString(R.string.failed_with_status_code, statusCode));
            app.addMessage(ref + ": " + app.getString(R.string.failed_with_status_code, statusCode)
                    + " - will retry");
            // Honour Retry-After rather than immediately re-hitting a server that
            // has asked us to slow down. Bounded, because a worker thread is not
            // ours to block indefinitely.
            int requested = Math.min(
                    uploader.getRetryAfterSeconds(),
                    SmsStoreWorkerStatusHandling.MAX_RETRY_AFTER_SECONDS);
            if (requested > 0) {
                Log.i(TAG, "Server asked for " + requested + "s before retrying; waiting");
                app.addMessage(ref + ": waiting " + requested + "s as the server requested");
                try {
                    Thread.sleep(requested * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return GIVE_UP;
                }
            }
            return GIVE_UP;
        }

        // The server has refused this one message and will refuse it again, so
        // retrying cannot help. Record it and move on: aborting here used to wedge
        // the sync permanently, because the watermark never advanced past a
        // message that always failed.
        Log.w(TAG, ref + " refused by server (status " + statusCode + "); not retrying");
        app.addMessage(ref + ": "
                + app.getString(R.string.refused_by_server_with_status_code, statusCode));
        DigestCache.set(context, hash, statusCode);
        return statusCode;
    }
}