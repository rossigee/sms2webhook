package org.golder.sms2webhook;

import android.os.Bundle;
import android.util.Log;

import androidx.preference.PreferenceFragmentCompat;

/**
 * Settings fragment that loads the preferences from the XML resource.
 */
public class SettingsFragment extends PreferenceFragmentCompat {
    private static final String TAG = SettingsFragment.class.getSimpleName();

    /**
     * Initializes the preferences with the provided root key.
     * 
     * @param savedInstanceState The saved instance state.
     * @param rootKey            The root key for the preferences.
     */
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Load the settings from the XML resource.
        setPreferencesFromResource(R.xml.preferences, rootKey);
    }
}
