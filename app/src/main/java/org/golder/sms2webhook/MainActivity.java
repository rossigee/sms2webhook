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
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final String APP_NAME = "sms2webhook";

    private SmsUploadService service;
    private boolean isBound = false;

    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check/acquire permissions
        requestPermissions(
                new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS},
                PERMISSION_REQUEST_CODE
        );
        // Set up main UI view
        setContentView(R.layout.activity_main);
        setupViews();
        // Add app tool/menu bar (for settings and other actions)
        setupToolbar();
        // Start service
        startService(new Intent(this, SmsUploadService.class));

        // Initialize statistics
        Statistics stats = Statistics.getInstance();
        stats.setWidgets(findViewById(R.id.textView), findViewById(R.id.progressBar));
    }

    private void setupViews() {
        ScrollView scrollView = findViewById(R.id.scrollView);
        TextView textView = findViewById(R.id.textView);
        ProgressBar progressBar = findViewById(R.id.progressBar);
        textView.setMovementMethod(new ScrollingMovementMethod());
        textView.setText("Starting main activity...\n");
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.app_toolbar);
        setSupportActionBar(toolbar);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, SmsUploadService.class);
        isBound = bindService(intent, connection, Context.BIND_AUTO_CREATE);
        if (!isBound) {
            Log.e(APP_NAME, "Unable to bind to service");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (service != null) {
        service.saveCache();
    }
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
            if (service != null) {
                service.refresh();
        }
            return true;
        }

        if (id == R.id.action_clear_cache) {
            if (service != null) {
                service.clearCache();
    }
            return true;
}

        if (id == R.id.action_quit) {
            finishAndRemoveTask();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            service = ((SmsUploadService.LocalBinder) binder).getService();
            Log.d(APP_NAME, "Main activity connected to service");
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            service = null;
            Log.d(APP_NAME, "Main activity disconnected from service");
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Check if a permission was requested and granted
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    // If any of the permissions are denied, do something
                    Log.e(APP_NAME, "Permission: " + permissions[i] + " was denied.");
                }
            }
        }
    }
}