package org.golder.sms2webhook;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import androidx.appcompat.widget.Toolbar;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

public class MainActivity extends AppCompatActivity {
    //private static final String APP_NAME = "sms2webhook";
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int PERMISSION_REQUEST_CODE = 1;

    private TextView textView;
    private ProgressBar progressBar;

    private DigestCache cache;

    private Statistics stats;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check/acquire permissions
        requestPermissions(
                new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS},
                PERMISSION_REQUEST_CODE
        );

        // Set up UI
        setupViews();
        setupToolbar();

        // Initialise cache and statistics
        Context ctx = getApplicationContext();

        stats = Statistics.getInstance();
    }

//    public void runWorker() {
//        // Start periodic worker to process SMS store
//        Log.i(TAG, "Running SMS Store Worker...");
//        Constraints.Builder builder = new Constraints.Builder()
//                .setRequiredNetworkType(NetworkType.CONNECTED);
//        Data.Builder data = new Data.Builder();
//        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SmsStoreWorker.class)
//                .addTag("poll")
//                .setInputData(data.build())
//                .setConstraints(builder.build())
//                .setInitialDelay(DELAY_SECS, TimeUnit.SECONDS)
//                .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_SECS, TimeUnit.SECONDS)
//                .build();
//        WorkManager instance = WorkManager.getInstance(this);
//        if (instance == null) {
//            Log.e(TAG, "WorkManager is null");
//            return;
//        }
//        instance.enqueue(request);
//    }

    private void setupViews() {
        setContentView(R.layout.activity_main);

        textView = findViewById(R.id.textView);
        textView.setMovementMethod(new ScrollingMovementMethod());
        textView.setText("Starting main activity...\n");
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.app_toolbar);
        setSupportActionBar(toolbar);
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
            Context ctx = getApplicationContext();
            Toast.makeText(ctx, "REFRESH BUTTON PRESSED!", Toast.LENGTH_SHORT);
            // doSomething()!
            return true;
        }

        if (id == R.id.action_clear_cache) {
            Context ctx = getApplicationContext();
            cache.clear(ctx);
            return true;
        }

        if (id == R.id.action_quit) {
            finishAndRemoveTask();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

//    private final ServiceConnection connection = new ServiceConnection() {
//        @Override
//        public void onServiceConnected(ComponentName className, IBinder binder) {
//            service = ((SmsUploadService.LocalBinder) binder).getService();
//            Log.d(TAG, "Connected to service");
//        }
//
//        @Override
//        public void onServiceDisconnected(ComponentName className) {
//            service = null;
//            Log.d(TAG, "Disconnected from service");
//        }
//    };
//
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Check if a permission was requested and granted
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    // If any of the permissions are denied, do something
                    Log.e(TAG, "Permission: " + permissions[i] + " was denied.");
                }
            }
        }
    }
}