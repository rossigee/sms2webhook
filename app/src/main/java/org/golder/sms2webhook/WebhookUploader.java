package org.golder.sms2webhook;

import android.content.SharedPreferences;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;

public class WebhookUploader {
    public static void upload(JSONObject msg, String objectname, SharedPreferences prefs)
            throws IllegalArgumentException, AlreadyExistsException {
        // Validate inputs
        if (msg == null || objectname == null || objectname.isEmpty() || prefs == null) {
            throw new IllegalArgumentException("Invalid arguments provided");
        }

        // Get the webhook URL from SharedPreferences
        String webhookUrl = prefs.getString("webhook_url", null);
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            throw new IllegalArgumentException("Webhook URL not set in SharedPreferences");
        }

        // Create a URL object from the webhook URL
        HttpURLConnection conn;
        try {
            URL url = new URL(webhookUrl);
            conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setDoOutput(true);

            // Convert the JSON object to bytes and send it through the output stream
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = msg.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Check response code
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_CONFLICT) {
                throw new AlreadyExistsException("Object already exists: " + objectname);
            } else if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new RuntimeException("Failed to upload, response code: " + responseCode);
            }
        }
        catch(ProtocolException pe) {
            throw new RuntimeException("Unexpected protocol exception: " + pe);
        }
        catch(MalformedURLException mie) {
            throw new RuntimeException("Malformed URL. Please check URL in settings: " + mie);
        }
        catch(IOException ioe) {
            throw new RuntimeException("Unexpected I/O exception: " + ioe);
        }
        conn.disconnect();

    }
}
