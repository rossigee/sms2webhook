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

    SmsUploadService service;
    private boolean isBound = false;

    Handler handler;

    public final String APP_NAME = "sms2webhook";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check/acquire permissions
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.READ_SMS,
                        Manifest.permission.RECEIVE_SMS
                },
                PackageManager.PERMISSION_GRANTED);

        // Set up main UI view
        setContentView(R.layout.activity_main);
        ScrollView scrollView = findViewById(R.id.scrollView);
        TextView textView = findViewById(R.id.textView);
        ProgressBar progress = findViewById(R.id.progressBar);
        textView.setMovementMethod(new ScrollingMovementMethod());
        textView.setText("Starting main activity...\n");

        // Add app tool/menu bar (for settings and other actions)
        Toolbar appToolbar = findViewById(R.id.app_toolbar);
        setSupportActionBar(appToolbar);

        // Set widgets that Statistics can manage
        Statistics stats = Statistics.getInstance();
        stats.setWidgets(textView, progress);

        // Start service
        Intent intent = new Intent(this, SmsUploadService.class);
        startService(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, SmsUploadService.class);
        isBound = bindService(intent, connection, Context.BIND_AUTO_CREATE);
        if(!isBound) {
            Log.e("main", "Unable to bind to service.");
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

    private final ServiceConnection connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder binder) {
            service = ((SmsUploadService.LocalBinder)binder).getService();
            Log.d("main", "Main activity connected to service");
        }

        public void onServiceDisconnected(ComponentName className) {
            service = null;
            Log.d("main", "Main activity disconnected from service");
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        service.save_cache();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.settings_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        if (id == R.id.action_refresh) {
            service.refresh();
            return true;
        }
        if (id == R.id.action_clear_cache) {
            service.clear_cache();
            return true;
        }
        if (id == R.id.action_quit) {
            finishAndRemoveTask();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}