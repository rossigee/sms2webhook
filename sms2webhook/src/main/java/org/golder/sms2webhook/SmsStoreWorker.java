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
                            Log.i(TAG, app.getString(R.string.uploaded_with_status_code, statusCode));
                            app.addMessage(ref + ": " + app.getString(R.string.uploaded_with_status_code, statusCode));
                        } else if (statusCode >= 400) {
                            Log.e(TAG, app.getString(R.string.failed_with_status_code, statusCode));
                            app.addMessage(ref + ": " + app.getString(R.string.failed_with_status_code, statusCode) + " - aborting sync");
                            DigestCache.set(context, hash, statusCode);
                            prefs.edit().putInt("watermark", app.getWatermark()).apply();
                            app.updateStats();
                            return Result.failure();
                        } else {
                            Log.i(TAG, app.getString(R.string.failed_with_status_code, statusCode));
                            app.addMessage(ref + ": " + app.getString(R.string.failed_with_status_code, statusCode));
                        }
                    } catch (WebhookUploader.WebhookUploadException e) {
                        Log.e(TAG, "Upload exception: " + e.getMessage());
                        app.addMessage(ref + ": Upload failed - " + e.getMessage());
                        statusCode = -1;
                    }
                    DigestCache.set(context, hash, statusCode);
                }

                app.setWatermark(app.getWatermark() + 1);
            }

            prefs.edit().putInt("watermark", app.getWatermark()).apply();
            app.updateStats();

            return Result.success();
        }
    }
}
