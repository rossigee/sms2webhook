package org.golder.sms2webhook;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

public class MainActivity extends AppCompatActivity {
    //private static final String APP_NAME = "sms2webhook";
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int PERMISSION_REQUEST_CODE = 1;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Check if a permission was requested and granted
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    // If any of the permissions are denied, do something
                    Log.e(TAG, "Permission: " + permissions[i] + " was denied.");
                    ((MainApplication) getApplication()).addMessage("ERROR: Permission: " + permissions[i] + " was denied.");
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Allow main application to add messages to message panel
        MainApplication mainApplication = (MainApplication) getApplication();
        mainApplication.setMainActivity(this);

        // Check/acquire permissions
        if (checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_DENIED) {
            requestPermissions(
                    new String[]{
                            Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.READ_SMS
                    },
                    PERMISSION_REQUEST_CODE
            );
            return;
        }

        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.app_toolbar);
        setSupportActionBar(toolbar);

        mainApplication.addMessage(getString(R.string.started_main_activity));
        mainApplication.restoreMessages();

        mainApplication.updateStats();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.settings_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }

        if (id == R.id.action_refresh) {
            Log.d(TAG, "Refresh button pressed");

            // Reset watermark to zero
            MainApplication app = (MainApplication) getApplication();
            app.setWatermark(0);
            app.updateStats();

            // Run store worker
            Handler handler = new Handler(Looper.getMainLooper());
            Context ctx = getApplicationContext();
            return handler.post(new SmsStoreWorkerRunnable(ctx));
        }

        if (id == R.id.action_clear_cache) {
            Log.d(TAG, "Clear button pressed");

            // Reset watermark to zero
            MainApplication app = (MainApplication) getApplication();
            app.setWatermark(0);

            // Clear cache
            Context ctx = getApplicationContext();
            DigestCache.clear(ctx);
            app.updateStats();
            return true;
        }

        if (id == R.id.action_quit) {
            finishAndRemoveTask();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setWatermark(Context ctx, int value) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("watermark", value);
        editor.apply();
    }

    public void restoreMessages() {
        MainApplication app = (MainApplication) getApplication();

        runOnUiThread(() -> {
            TextView activityLogTextView = findViewById(R.id.textView);
            String[] messages = app.getMessages();
            activityLogTextView.setText(String.join("\n", messages) + "\n");
        });
    }

    public void addMessage(String line) {
        runOnUiThread(() -> {
            TextView activityLogTextView = findViewById(R.id.textView);
            activityLogTextView.append(line + "\n");
            ScrollView scrollView = findViewById(R.id.scrollView);
            scrollView.post(() -> {
                scrollView.fullScroll(scrollView.FOCUS_DOWN);
            });
        });
    }

    public void updateStats(Context ctx) {
        MainApplication app = (MainApplication) getApplication();

        runOnUiThread(() -> {
            TextView storeCountTextView = findViewById(R.id.storeCountTextView);
            storeCountTextView.setText(getString(R.string.store_count, app.inboxCount));

            TextView sentCountTextView = findViewById(R.id.sentCountTextView);
            sentCountTextView.setText(getString(R.string.sent_count, app.sentCount));

            TextView unsentCountTextView = findViewById(R.id.unsentCountTextView);
            unsentCountTextView.setText(getString(R.string.retry_count, app.unsentCount));

            ProgressBar progressBar = findViewById(R.id.progressBar);
            progressBar.setMin(0);
            progressBar.setMax(100);
            progressBar.setProgress(Math.round(app.watermark * 100 / app.inboxCount));
        });
    }
}