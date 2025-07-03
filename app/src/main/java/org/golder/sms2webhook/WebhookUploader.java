package org.golder.sms2webhook;

import android.util.Log;

import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Handles uploading data to a webhook URL.
 */
public class WebhookUploader {
    private static final String TAG = WebhookUploader.class.getSimpleName();

    private final String webhookUrl;
    private final String apiKey;

    public WebhookUploader(String url, String apiKey) {
        this.webhookUrl = url;
        this.apiKey = apiKey;
    }

    /**
     * Uploads data to a webhook URL.
     *
     * @param jsonString the JSON data as string to be uploaded
     * @return true if successful (status 200), false otherwise
     */
    public boolean upload(String jsonString) {
        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            int responseCode = upload(jsonObject);
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (Exception e) {
            Log.e(TAG, "Error uploading JSON string: " + e.getMessage());
            return false;
        }
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
            conn.setRequestProperty("Authorization", String.format("Bearer %s", apiKey));
            conn.setDoOutput(true);
            byte[] input = msg.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(input, 0, input.length);
            }
            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Error POSTing message: Status code " + responseCode);
            }
            return responseCode;
        } catch (ProtocolException e) {
            Log.e(TAG, "ProtocolException POSTing message: " + e);
            return -1;
        } catch (MalformedURLException e) {
            Log.e(TAG, "MalformedURLException POSTing message: " + e);
            return -1;
        } catch (IOException e) {
            Log.e(TAG, "IOException POSTing message: " + e);
            return -1;
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