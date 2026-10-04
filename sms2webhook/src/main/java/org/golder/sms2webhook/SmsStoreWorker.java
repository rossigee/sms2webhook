package org.golder.sms2webhook;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.provider.Telephony;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
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

    private final Context context;

    public SmsStoreWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
        this.context = context;
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
        String webhookUrl = prefs.getString("webhook_url", "");
        if (webhookUrl.isEmpty()) {
            Log.e(TAG, "Webhook URL not defined.");
            app.addMessage("ERROR: Webhook URL not defined.");
            return Result.failure();
        }
        String apiKey = prefs.getString("api_key", "");

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
                app.updateStats();
                return Result.success();
            }

            if (app.getWatermark() > total) {
                app.setWatermark(total);
            }

            while (app.getWatermark() < total) {
                if (isStopped()) {
                    Log.i(TAG, "Worker stopped. Aborting sync.");
                    app.addMessage("Sync stopped by user.");
                    prefs.edit().putInt("watermark", app.getWatermark()).apply();
                    app.updateStats();
                    return Result.failure();
                }

                String ref = "[" + (app.getWatermark() + 1) + " / " + total + "]";
                Log.i(TAG, app.getString(R.string.processing, ref));

                if (!cursor.moveToPosition(app.getWatermark())) {
                    Log.e(TAG, app.getString(R.string.unable_to_move_cursor_to_watermark_position, app.getWatermark()));
                    app.addMessage(app.getString(R.string.unable_to_move_cursor_to_watermark_position, app.getWatermark()));
                    prefs.edit().putInt("watermark", app.getWatermark()).apply();
                    app.updateStats();
                    return Result.failure();
                }

                JSONObject json = encodeMessage(cursor);
                String hash;
                try {
                    hash = DigestUtil.getHexSHA256Hash(json.toString().getBytes(StandardCharsets.UTF_8));
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
                Log.d(TAG, "Looking up msghash " + hash + "...");
                if (DigestCache.get(context, hash) == HttpURLConnection.HTTP_OK) {
                    Log.i(TAG, "Already successfully sent msghash " + hash + ". Skipping.");
                    app.addMessage(ref + ": Already sent. Skipping.");
                } else {
                    WebhookUploader uploader = new WebhookUploader(webhookUrl, apiKey);
                    int statusCode = 0;
                    try {
                        statusCode = uploader.upload(json);
                        if (statusCode == HttpURLConnection.HTTP_OK) {
                            // A duplicate is still a success, but saying
                            // "Uploaded" for it hides that nothing was stored.
                            int messageResId = uploader.wasAlreadyExisted()
                                    ? R.string.already_existed_on_server
                                    : R.string.uploaded_with_status_code;
                            Log.i(TAG, app.getString(messageResId, statusCode));
                            app.addMessage(ref + ": " + app.getString(messageResId, statusCode));
                        } else if (SmsStoreWorkerStatusHandling.abortsSync(statusCode)) {
                            // A server fault or transport failure says nothing about
                            // the message, only about the connection. Stop, but ask
                            // WorkManager to come back rather than losing what follows.
                            Log.e(TAG, app.getString(R.string.failed_with_status_code, statusCode));
                            app.addMessage(ref + ": " + app.getString(R.string.failed_with_status_code, statusCode)
                                    + " - will retry");
                            // Honour Retry-After rather than immediately re-hitting a
                            // server that has asked us to slow down. Bounded, because
                            // a worker thread is not ours to block indefinitely.
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
                                    prefs.edit().putInt("watermark", app.getWatermark()).apply();
                                    app.updateStats();
                                    return Result.retry();
                                }
                            }
                            prefs.edit().putInt("watermark", app.getWatermark()).apply();
                            app.updateStats();
                            return Result.retry();
                        } else if (statusCode >= 400) {
                            // The server has rejected this one message and will
                            // reject it again, so retrying cannot help. Record it and
                            // move on: aborting here used to wedge the sync
                            // permanently, because the watermark never advanced past
                            // a message that always failed.
                            Log.w(TAG, ref + " rejected by server (status " + statusCode + "); skipping");
                            app.addMessage(ref + ": rejected by server (status " + statusCode + ") - skipped");
                            DigestCache.set(context, hash, statusCode);
                        } else {
                            Log.i(TAG, app.getString(R.string.failed_with_status_code, statusCode));
                            app.addMessage(ref + ": " + app.getString(R.string.failed_with_status_code, statusCode));
                        }
                    } catch (WebhookUploader.WebhookUploadException e) {
                        // A transport failure says nothing about the message, only
                        // about the connection. Treat it as transient and let
                        // WorkManager retry rather than advancing past it.
                        Log.e(TAG, "Upload exception: " + e.getMessage());
                        app.addMessage(ref + ": Upload failed - " + e.getMessage() + " - will retry");
                        prefs.edit().putInt("watermark", app.getWatermark()).apply();
                        app.updateStats();
                        return Result.retry();
                    }
                    DigestCache.set(context, hash, statusCode);
                }

                app.setWatermark(app.getWatermark() + 1);

                // Report position every message so the bar tracks the sync. The
                // cached counts are only refreshed periodically, because
                // updateStats() re-queries the cache and the SMS provider and is
                // far too expensive to run per message.
                app.reportSyncProgress(app.getWatermark(), total);
                if (app.getWatermark() % STATS_REFRESH_INTERVAL == 0) {
                    // Counts only. Diagnostics would append a log entry on every
                    // tick, filling the activity log with the same finding.
                    app.refreshStats(false);
                }
            }

            prefs.edit().putInt("watermark", app.getWatermark()).apply();
            app.updateStats();

            return Result.success();
        }
    }
}
