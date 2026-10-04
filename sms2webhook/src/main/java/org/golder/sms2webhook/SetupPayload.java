package org.golder.sms2webhook;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.regex.Pattern;

/**
 * Parses the setup payload carried in the QR code shown by the Odoo SMS Device
 * form.
 *
 * <p>Extracted from {@link SettingsFragment} so the parsing rules can be tested
 * without a camera.
 *
 * <p>The server sends one compact JSON object:
 * <pre>{"v":1,"url":"https://host/sms/upload","key":"...","device":"Pixel 8"}</pre>
 *
 * <p>Result and error are returned together as a {@link ParseResult} rather than
 * stashed in static fields, so a failure cannot be reported against a later
 * successful parse.
 */
final class SetupPayload {

    /** Bumped if the payload shape ever changes incompatibly. */
    static final int SUPPORTED_VERSION = 1;

    private static final Pattern HTTPS_URL =
            Pattern.compile("^https://[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]+$");

    private final String url;
    private final String key;

    private SetupPayload(String url, String key) {
        this.url = url;
        this.key = key;
    }

    String getUrl() {
        return url;
    }

    String getKey() {
        return key;
    }

    /** Outcome of {@link #parse(String)}: either a payload or a reason it failed. */
    static final class ParseResult {
        private final SetupPayload payload;
        private final String error;

        private ParseResult(SetupPayload payload, String error) {
            this.payload = payload;
            this.error = error;
        }

        static ParseResult ok(SetupPayload payload) {
            return new ParseResult(payload, null);
        }

        static ParseResult error(String message) {
            return new ParseResult(null, message);
        }

        boolean isSuccess() {
            return payload != null;
        }

        SetupPayload getPayload() {
            return payload;
        }

        /** @return a message suitable for a toast, or null on success */
        String getError() {
            return error;
        }
    }

    static ParseResult parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return ParseResult.error("The QR code was empty.");
        }

        JSONObject json;
        try {
            json = new JSONObject(raw.trim());
        } catch (JSONException e) {
            return ParseResult.error("That is not an SMS2Webhook setup code.");
        }

        int version = json.optInt("v", 0);
        if (version != SUPPORTED_VERSION) {
            return ParseResult.error(version == 0
                    ? "That QR code is missing a version field."
                    : "That setup code is version " + version + "; this app supports "
                            + "version " + SUPPORTED_VERSION + ".");
        }

        String url = json.optString("url", "").trim();
        String key = json.optString("key", "").trim();

        if (url.isEmpty()) {
            return ParseResult.error("The setup code has no endpoint URL.");
        }
        if (!HTTPS_URL.matcher(url).matches()) {
            // Enforced here as well as in SettingsFragment, so a scanned code
            // cannot smuggle in a cleartext or malformed endpoint.
            return ParseResult.error("The setup code's URL must be HTTPS.");
        }
        if (key.isEmpty()) {
            return ParseResult.error("The setup code has no API key.");
        }

        return ParseResult.ok(new SetupPayload(url, key));
    }

    /**
     * Persist a successfully parsed payload into the app's preferences, using the
     * same store the settings screen reads and writes.
     *
     * @return true if both values were stored
     */
    static boolean save(Context context, SetupPayload payload) {
        if (context == null || payload == null) {
            return false;
        }
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.edit()
                .putString("webhook_url", payload.getUrl())
                .putString("api_key", payload.getKey())
                .commit();
    }
}
