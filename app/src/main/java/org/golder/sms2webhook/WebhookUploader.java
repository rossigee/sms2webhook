package org.golder.sms2webhook;

import android.content.SharedPreferences;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Handles uploading data to a webhook URL.
 */
public class WebhookUploader {

    private static final String TAG = WebhookUploader.class.getSimpleName();

    /**
     * Uploads data to a webhook URL.
     *
     * @param msg        the JSON data to be uploaded
     * @param objectName the name of the object being uploaded
     * @param prefs      the SharedPreferences instance containing the webhook URL
     * @throws WebhookUploadException if an error occurs during the upload process
     */
    public static void upload(JSONObject msg, String objectName, SharedPreferences prefs) throws WebhookUploadException {
        validateInputs(msg, objectName, prefs);
        String webhookUrl = getWebhookUrl(prefs);

        try {
            HttpURLConnection conn = getHttpURLConnection(webhookUrl);
            sendPostRequest(conn, msg);
            handleResponseCode(conn, objectName);
        } catch (WebhookUploadException e) {
            throw e; // Rethrow WebhookUploadException
        } catch (Exception e) {
            throw new WebhookUploadException("Unexpected error during upload: " + e.getMessage(), e);
        } finally {
            disconnectConnection();
        }
    }

    private static void validateInputs(JSONObject msg, String objectName, SharedPreferences prefs) throws WebhookUploadException {
        if (msg == null || objectName == null || objectName.isEmpty() || prefs == null) {
            throw new WebhookUploadException("Invalid arguments provided");
        }
    }

    private static String getWebhookUrl(SharedPreferences prefs) throws WebhookUploadException {
        String webhookUrl = prefs.getString("webhook_url", null);
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            throw new WebhookUploadException("Webhook URL not set in SharedPreferences");
        }
        return webhookUrl;
    }

    private static HttpURLConnection getHttpURLConnection(String webhookUrl) throws MalformedURLException, WebhookUploadException {
            URL url = new URL(webhookUrl);
        try {
            return (HttpURLConnection) url.openConnection();
        } catch (IOException e) {
            throw new WebhookUploadException("Error opening connection to webhook URL: " + e.getMessage(), e);
        }
    }

    private static void sendPostRequest(HttpURLConnection conn, JSONObject msg) throws WebhookUploadException {
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setDoOutput(true);

                byte[] input = msg.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(input, 0, input.length);
            }
        } catch (IOException e) {
            throw new WebhookUploadException("Error sending post request: " + e.getMessage(), e);
        }
    }

    private static void handleResponseCode(HttpURLConnection conn, String objectName) throws WebhookUploadException {
        int responseCode = getResponseCode(conn);
            if (responseCode == HttpURLConnection.HTTP_CONFLICT) {
            throw new WebhookUploadException("Object already exists: " + objectName, new ConflictException());
            } else if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new WebhookUploadException("Failed to upload, response code: " + responseCode);
            }
        }

    private static int getResponseCode(HttpURLConnection conn) throws WebhookUploadException {
        try {
            return conn.getResponseCode();
        } catch (IOException e) {
            throw new WebhookUploadException("Error getting response code: " + e.getMessage(), e);
        }
    }

    private static void disconnectConnection() {
        try {
            HttpURLConnection conn = (HttpURLConnection) WebhookUploader.class.getDeclaredField("conn").get(null);
            if (conn != null) {
                conn.disconnect();
            }
        } catch (Exception e) {
            // Ignore exception
        }
    }

    // Custom exception classes
    public static class WebhookUploadException extends Exception {
        public WebhookUploadException(String message) {
            super(message);
        }

        public WebhookUploadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class ConflictException extends Exception {
        public ConflictException() {
            super("Object already exists");
        }
    }
}