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

    /**
     * Header the server sets to true when the payload was already stored and the
     * upload therefore changed nothing.
     */
    static final String HEADER_ALREADY_EXISTED = "X-Already-Existed";

    private final String webhookUrl;
    private final String apiKey;

    private boolean lastUploadAlreadyExisted = false;

    public WebhookUploader(String url, String apiKey) {
        this.webhookUrl = url;
        this.apiKey = apiKey;
    }

    /**
     * Whether the most recent {@link #upload(JSONObject)} response carried
     * {@code X-Already-Existed: true}.
     *
     * <p>A server-side duplicate still returns 200, so this is the only way to
     * tell "stored a new message" from "recognised one we already had". Worth
     * surfacing: it is how a restored or wiped device discovers that its upload
     * history overlaps something the server kept.
     *
     * @return true only when the last response explicitly said the payload was
     *         already present; false when absent, unparseable, or no upload has
     *         been attempted yet
     */
    public boolean wasAlreadyExisted() {
        return lastUploadAlreadyExisted;
    }

    /**
     * Interprets an {@code X-Already-Existed} header value.
     *
     * <p>Only an explicit "true" counts. A missing header, an empty value, or
     * anything else is treated as "not a duplicate" so an unexpected value can
     * never be reported to the user as one.
     *
     * @param headerValue raw header value, may be null
     * @return true if the value is exactly "true", ignoring case and surrounding
     *         whitespace
     */
    static boolean isAlreadyExistedHeader(String headerValue) {
        return headerValue != null && "true".equalsIgnoreCase(headerValue.trim());
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
            if (apiKey != null && !apiKey.isEmpty()) {
                conn.setRequestProperty("Authorization", String.format("Bearer %s", apiKey));
            }
            conn.setDoOutput(true);
            byte[] input = msg.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(input, 0, input.length);
            }
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                lastUploadAlreadyExisted =
                        isAlreadyExistedHeader(conn.getHeaderField(HEADER_ALREADY_EXISTED));
            }
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