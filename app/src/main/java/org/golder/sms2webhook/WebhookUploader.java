package org.golder.sms2webhook;

import android.content.SharedPreferences;
import android.util.Log;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import androidx.preference.PreferenceManager;

/**
 * Handles uploading data to a webhook URL.
 */
public class WebhookUploader {
    private static final String TAG = WebhookUploader.class.getSimpleName();

    private String webhookUrl;

    public WebhookUploader(String url) {
        webhookUrl = url;
    }

    /**
     * Uploads data to a webhook URL.
     *
     * @param msg        the JSON data to be uploaded
     * @throws WebhookUploadException if an error occurs during the upload process
     */
    public int upload(JSONObject msg) throws WebhookUploadException {
        HttpURLConnection conn = null;
        try {
            conn = getHttpURLConnection(webhookUrl);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setDoOutput(true);
            byte[] input = msg.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(input, 0, input.length);
            }
            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Error POSTing message: Status code " + String.valueOf(responseCode));
            }
            return responseCode;
        } catch (Exception e) {
            throw new WebhookUploadException("Unexpected error during upload: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static HttpURLConnection getHttpURLConnection(String webhookUrl) throws MalformedURLException, WebhookUploadException {
        URL url = new URL(webhookUrl);
        try {
            return (HttpURLConnection) url.openConnection();
        } catch (IOException e) {
            throw new WebhookUploadException("Error opening connection to webhook URL: " + e.getMessage(), e);
        }
    }

    public static class WebhookUploadException extends Exception {
        public WebhookUploadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}