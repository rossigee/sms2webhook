package org.golder.sms2webhook;

import android.os.Bundle;
import android.util.Patterns;
import android.widget.Toast;

import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Settings fragment that loads the preferences from the XML resource.
 */
public class SettingsFragment extends PreferenceFragmentCompat {
    private static final String TAG = SettingsFragment.class.getSimpleName();
    private ExecutorService executorService;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        executorService = Executors.newSingleThreadExecutor();
        setupPreferences();
    }

    private void setupPreferences() {
        // Setup webhook URL validation
        EditTextPreference webhookUrlPref = findPreference("webhook_url");
        if (webhookUrlPref != null) {
            webhookUrlPref.setOnPreferenceChangeListener((preference, newValue) -> {
                String url = newValue.toString().trim();
                if (url.isEmpty()) {
                    return true; // Allow empty URL
                }
                
                if (!isValidUrl(url)) {
                    Toast.makeText(getContext(), R.string.invalid_url, Toast.LENGTH_SHORT).show();
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
        EditTextPreference apiKeyPref = findPreference("api_key");
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
    }

    private boolean isValidUrl(String url) {
        return Patterns.WEB_URL.matcher(url).matches() && 
               (url.startsWith("http://") || url.startsWith("https://"));
    }

    private void testConnection() {
        EditTextPreference webhookUrlPref = findPreference("webhook_url");
        EditTextPreference apiKeyPref = findPreference("api_key");
        
        if (webhookUrlPref == null) return;
        
        String webhookUrl = webhookUrlPref.getText();
        String apiKey = apiKeyPref != null ? apiKeyPref.getText() : null;
        
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            Toast.makeText(getContext(), "Please set webhook URL first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (!isValidUrl(webhookUrl)) {
            Toast.makeText(getContext(), R.string.invalid_url, Toast.LENGTH_SHORT).show();
            return;
        }

        // Show testing message
        Toast.makeText(getContext(), "Testing connection...", Toast.LENGTH_SHORT).show();
        
        // Run test in background
        executorService.execute(() -> {
            try {
                // Create test webhook uploader
                WebhookUploader uploader = new WebhookUploader(webhookUrl, apiKey);
                
                // Test with empty data
                String testData = "{\"test\": true, \"timestamp\": " + System.currentTimeMillis() + "}";
                boolean success = uploader.upload(testData);
                
                // Update UI on main thread
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (success) {
                            Toast.makeText(getContext(), R.string.connection_test_success, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(getContext(), "Connection test failed", Toast.LENGTH_LONG).show();
                        }
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        String message = getString(R.string.connection_test_failed, e.getMessage());
                        Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
                    });
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
