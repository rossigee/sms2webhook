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
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

public class MainActivity extends AppCompatActivity {
    //private static final String APP_NAME = "sms2webhook";
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int PERMISSION_REQUEST_CODE = 1;

    private DigestCache cache;

    // UI components
    private TextView activityLogTextView;
    private ProgressBar progressBar;

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Check if a permission was requested and granted
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    // If any of the permissions are denied, do something
                    Log.e(TAG, "Permission: " + permissions[i] + " was denied.");
                    ((MainApplication)getApplication()).addMessage("ERROR: Permission: " + permissions[i] + " was denied.");
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Allow main application to add messages to message panel
        MainApplication mainApplication = (MainApplication)getApplication();
        mainApplication.setMainActivity(this);

        // Check/acquire permissions
        requestPermissions(
            new String[]{
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS
            },
            PERMISSION_REQUEST_CODE
        );

        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.app_toolbar);
        setSupportActionBar(toolbar);

        updateUI(getApplicationContext());
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
            Context ctx = getApplicationContext();
            clearWatermark(ctx);
            Handler handler = new Handler(Looper.getMainLooper());
            return handler.post(new SmsStoreWorkerRunnable(ctx));
        }

        if (id == R.id.action_clear_cache) {
            Log.d(TAG, "Clear button pressed");
            Context ctx = getApplicationContext();
            clearWatermark(ctx);
            cache.clear(ctx);
            updateUI(ctx);
            return true;
        }

        if (id == R.id.action_quit) {
            finishAndRemoveTask();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void clearWatermark(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("watermark", 0);
        editor.apply();
    }

    public void updateUI(Context ctx) {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                SmsRepository.Status status = SmsRepository.fetchStatus(ctx);

                TextView storeCountTextView = findViewById(R.id.storeCountTextView);
                storeCountTextView.setText("Store count" + " : " + status.inboxCount);

                TextView sentCountTextView = findViewById(R.id.sentCountTextView);
                sentCountTextView.setText("Sent count" + " : " + status.processedCount);

                TextView retryCountTextView = findViewById(R.id.retryCountTextView);
                retryCountTextView.setText("Retry count" + " : " + status.retryCount);

                progressBar = findViewById(R.id.progressBar);
                progressBar.setMin(0);
                progressBar.setMax(100);
                progressBar.setProgress(100 * (status.processedCount / status.inboxCount));

                Runnable r2 = new Runnable() {
                    @Override
                    public void run() {
                        TextView activityLogTextView = findViewById(R.id.textView);
                        String[] messages = status.messages;
                        activityLogTextView.setText(String.join("\n", messages));
                    }
                };
                runOnUiThread(r2);
            }
        };
        new Thread(r).start();
    }

    public void addMessage(Context ctx, String line) {
        Runnable r = new Runnable() {
            public void run() {
                TextView activityLogTextView = findViewById(R.id.textView);
                activityLogTextView.append(line + "\n");
            }
        };
        runOnUiThread(r);
    }
}