package org.golder.sms2webhook;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanIntentResult;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Settings fragment that loads the preferences from the XML resource.
 */
public class SettingsFragment extends PreferenceFragmentCompat {
    private static final String TAG = SettingsFragment.class.getSimpleName();

    private ExecutorService executorService;

    // Held so summaries can be refreshed after a QR scan writes to the shared
    // preference store. Re-inflating the preference hierarchy would duplicate it.
    private EditTextPreference webhookUrlPref;
    private EditTextPreference apiKeyPref;

    // Uses ZXing's own embedded CaptureActivity rather than the ACTION_SCAN
    // intent: the intent form redirects to a Play Store install when no scanner
    // app is present, which is not acceptable for a self-hosted family app.
    private final ScanContract scanContract = new ScanContract();
    private ActivityResultLauncher<ScanOptions> scanLauncher;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        executorService = Executors.newSingleThreadExecutor();

        scanLauncher = registerForActivityResult(scanContract, this::onScanResult);

        setupPreferences();
    }

    private void setupPreferences() {
        // Setup webhook URL validation
        webhookUrlPref = findPreference("webhook_url");
        if (webhookUrlPref != null) {
            webhookUrlPref.setOnPreferenceChangeListener((preference, newValue) -> {
                String url = newValue.toString().trim();
                if (url.isEmpty()) {
                    return true; // Allow empty URL
                }
                if (!Patterns.WEB_URL.matcher(url).matches()) {
                    Toast.makeText(getContext(), R.string.invalid_url, Toast.LENGTH_SHORT).show();
                    return false;
                }
                if (!url.startsWith("https://")) {
                    Toast.makeText(getContext(), R.string.invalid_url_https_required, Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            });

            // Update summary with current value
            webhookUrlPref.setSummaryProvider(preference -> {
                String value = ((EditTextPreference) preference).getText();
                return value != null && !value.isEmpty() ? value : getString(R.string.webhook_url_hint);
            });
        }

        // Setup API key preference
        apiKeyPref = findPreference("api_key");
        if (apiKeyPref != null) {
            apiKeyPref.setSummaryProvider(preference -> {
                String value = ((EditTextPreference) preference).getText();
                return value != null && !value.isEmpty() ? "••••••••" : getString(R.string.api_key_hint);
            });
        }

        // Setup test connection
        Preference testConnectionPref = findPreference("test_connection");
        if (testConnectionPref != null) {
            testConnectionPref.setOnPreferenceClickListener(preference -> {
                testConnection();
                return true;
            });
        }

        // Scan the server-issued setup QR code to fill in URL and API key
        Preference scanPref = findPreference("scan_setup_qr");
        if (scanPref != null) {
            scanPref.setOnPreferenceClickListener(preference -> {
                scanSetupQr();
                return true;
            });
        }
    }

    /**
     * Launch the QR scanner.
     *
     * <p>Guards on camera hardware first: the manifest declares the camera
     * feature optional so the app still installs on devices without one, which
     * means a missing camera has to be reported rather than thrown.
     */
    private void scanSetupQr() {
        if (getContext() == null) {
            return;
        }
        if (!getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            Toast.makeText(getContext(), R.string.scan_camera_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt(getString(R.string.scan_qr_prompt));
        options.setBeepEnabled(false);
        options.setOrientationLocked(false);
        scanLauncher.launch(options);
    }

    private void onScanResult(@Nullable ScanIntentResult result) {
        if (result == null || result.getContents() == null) {
            // Cancelled or dismissed - leave existing settings untouched.
            return;
        }

        SetupPayload.ParseResult parsed = SetupPayload.parse(result.getContents());
        if (!parsed.isSuccess()) {
            Toast.makeText(getContext(), parsed.getError(), Toast.LENGTH_LONG).show();
            return;
        }

        if (!SetupPayload.save(requireContext(), parsed.getPayload())) {
            Toast.makeText(getContext(), R.string.scan_failed_to_save, Toast.LENGTH_LONG).show();
            return;
        }

        refreshFromPreferences();
        Toast.makeText(getContext(), R.string.scan_config_applied, Toast.LENGTH_LONG).show();
    }

    /**
     * Re-read the shared preference store into the visible preferences so a scan
     * is reflected immediately rather than on next visit.
     */
    private void refreshFromPreferences() {
        if (webhookUrlPref != null && webhookUrlPref.getSharedPreferences() != null) {
            String url = webhookUrlPref.getSharedPreferences().getString("webhook_url", "");
            if (url != null && !url.equals(webhookUrlPref.getText())) {
                webhookUrlPref.setText(url);
            }
        }
        if (apiKeyPref != null && apiKeyPref.getSharedPreferences() != null) {
            String key = apiKeyPref.getSharedPreferences().getString("api_key", "");
            if (key != null && !key.equals(apiKeyPref.getText())) {
                apiKeyPref.setText(key);
            }
        }
    }

    private boolean isValidUrl(String url) {
        return Patterns.WEB_URL.matcher(url).matches() &&
                url.startsWith("https://");
    }

    private void testConnection() {
        if (webhookUrlPref == null) return;

        String webhookUrl = webhookUrlPref.getText();
        String apiKey = apiKeyPref != null ? apiKeyPref.getText() : null;

        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            Toast.makeText(getContext(), R.string.webhook_url_required, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isValidUrl(webhookUrl)) {
            Toast.makeText(getContext(), R.string.invalid_url, Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(getContext(), R.string.testing_connection, Toast.LENGTH_SHORT).show();

        // Run test in background
        executorService.execute(() -> {
            boolean succeeded = false;
            String failureDetail = null;
            try {
                WebhookUploader uploader = new WebhookUploader(webhookUrl, apiKey);
                String testData = "{\"test\": true, \"timestamp\": " + System.currentTimeMillis() + "}";
                succeeded = uploader.upload(testData);
            } catch (Exception e) {
                failureDetail = e.getMessage() == null ? e.toString() : e.getMessage();
            }

            // Hand the outcome back as data and resolve the message on the main
            // thread. Reading a string resource from here would run against a
            // fragment that may already be detached, which throws on the main
            // thread out of a callback the user never initiated.
            Activity activity = getActivity();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                return;
            }

            String message;
            if (succeeded) {
                message = getString(R.string.connection_test_success);
            } else if (failureDetail != null) {
                message = getString(R.string.connection_test_failed, failureDetail);
            } else {
                message = getString(R.string.connection_test_failed_generic);
            }

            activity.runOnUiThread(() -> {
                if (isAdded()) {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    @Override
    public void onDestroy() {
        if (executorService != null) {
            executorService.shutdown();
        }
        super.onDestroy();
    }
}
