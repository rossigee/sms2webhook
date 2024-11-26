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

    private Context context;

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

        MainApplication app = (MainApplication)context.getApplicationContext();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String webhookUrl = prefs.getString("webhook_url", "");
        if(webhookUrl.equals("")) {
            Log.e(TAG, "Webhook URL not defined.");
            app.addMessage("ERROR: Webhook URL not defined.");
            return Result.failure();
        }

        Cursor cursor = context.getContentResolver().query(Telephony.Sms.CONTENT_URI, null, null, null, "_id");
        int total = cursor.getCount();
        Log.i(TAG, "SMS message count: " + String.valueOf(total));
        if(total == 0) {
            Log.i(TAG, "Empty SMS inbox.");
            app.addMessage("Empty SMS inbox.");
            app.updateStats();
            return Result.success();
        }

        // Move cursor to watermark
        if(app.watermark > total) {
            app.watermark = total;
        }

        while(app.watermark < total) {
            String ref = "[" + (app.watermark + 1) + " / " + total + "]";
            Log.i(TAG, app.getString(R.string.processing, ref));
//            app.addMessage("Processing " + (app.watermark + 1) + " / " + total);
//            String msgData = "";
//            for (int idx = 0; idx < cursor.getColumnCount(); idx++) {
//                Log.i(TAG, "idx(" + idx + "): name '" + cursor.getColumnName(idx) + "' = " + cursor.getString(idx));
//            }

            if (!cursor.moveToPosition(app.watermark)) {
                Log.e(TAG, app.getString(R.string.unable_to_move_cursor_to_watermark_position, app.watermark));
                app.addMessage(app.getString(R.string.unable_to_move_cursor_to_watermark_position, app.watermark));
                return Result.failure();
            }

            // Check we haven't previously uploaded this payload
            JSONObject json = encodeMessage(cursor);
            String hash = null;
            try {
                hash = DigestUtil.getHexSHA256Hash(json.toString().getBytes(StandardCharsets.UTF_8));
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
            Log.d(TAG, "Looking up msghash " + hash + "...");
            if (DigestCache.get(context, hash) == HttpURLConnection.HTTP_OK) {
                Log.i(TAG, "Already successfully sent msghash " + hash + ". Skipping.");
                app.addMessage(ref + ": " + "Already sent. Skipping.");
            }
            else {
                // Upload and record status code against digest in cache
                WebhookUploader uploader = new WebhookUploader(webhookUrl);
                int statusCode = 0;
                try {
                    statusCode = uploader.upload(json);
                    if(statusCode == HttpURLConnection.HTTP_OK) {
                        Log.i(TAG, app.getString(R.string.uploaded_with_status_code, statusCode));
                        app.addMessage(ref + ": " + app.getString(R.string.uploaded_with_status_code, statusCode));
                    } else {
                        Log.i(TAG, app.getString(R.string.failed_with_status_code, statusCode));
                        app.addMessage(ref + ": " + app.getString(R.string.failed_with_status_code, statusCode));
                    }
                } catch (WebhookUploader.WebhookUploadException e) {
                    throw new RuntimeException(e);
                }
                DigestCache.set(context, hash, statusCode);
            }

            app.setWatermark(app.watermark + 1);
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("watermark", app.watermark);
        editor.apply();
        app.updateStats();

        return Result.success();
    }
}