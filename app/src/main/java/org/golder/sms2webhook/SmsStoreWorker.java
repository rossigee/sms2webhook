package org.golder.sms2webhook;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.telephony.SmsMessage;
import android.util.Log;

import org.json.JSONObject;
import org.json.JSONArray;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.net.HttpURLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.json.JSONException;

import org.golder.sms2webhook.DigestUtil;

public class SmsStoreWorker extends Worker {
    private static final String TAG = SmsStoreWorker.class.getSimpleName();

    private Context context;

    public SmsStoreWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
        this.context = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, "Working...");

        // Unpack message
        Data inputData = getInputData();
        byte[] pdu = inputData.getByteArray("pdu");
        if(pdu == null) {
            Log.w(TAG, "Attempting to process work item with a Null PDU.");
            //Toast.makeText(ctx, e.getMessage(), Toast.LENGTH_SHORT);
            return Result.failure();
        }

        String format = inputData.getString("format");

        // Determine if we've already successfully sent this one
        try {
            String hash = DigestUtil.getHexSHA256Hash(pdu);
            Log.i(TAG, "Looking up msghash " + hash + "...");
            if (DigestCache.get(context, hash) == String.valueOf(HttpURLConnection.HTTP_OK)) {
                Log.i(TAG, "Skipping already sent msghash " + hash + ".");
                return Result.success();
            }

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String webhookUrl = prefs.getString("webhook_url", "");
            if(webhookUrl.equals("")) {
                Log.e(TAG, "Webhook URL not defined.");
                return Result.failure();
            }

            WebhookUploader uploader = new WebhookUploader(webhookUrl);
            SmsMessage message = SmsMessage.createFromPdu(pdu, format);
            int statusCode = uploader.upload(DigestUtil.toJSON(message));
            DigestCache.set(context, hash, String.valueOf(statusCode));

        } catch(NoSuchAlgorithmException e) {
            Log.e(TAG, "Unable to select SHA-256 message hash: " + e.toString());
            //Toast.makeText(ctx, e.getMessage(), Toast.LENGTH_SHORT);
            throw new RuntimeException(e);
        } catch (WebhookUploader.WebhookUploadException e) {
            Log.e(TAG, "Error sending message to webhook: " + e);
            //Toast.makeText(ctx, e.getMessage(), Toast.LENGTH_SHORT);
            throw new RuntimeException(e);
        }

        // Record result in cache
        return Result.success();
    }
}