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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int PERMISSION_REQUEST_CODE = 1;

    private MainViewModel viewModel;
    private LogAdapter logAdapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearProgressIndicator progressBar;
    private TextView storeCountTextView;
    private TextView sentCountTextView;
    private TextView unsentCountTextView;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    Log.e(TAG, "Permission: " + permissions[i] + " was denied.");
                    viewModel.addLogEntry("ERROR: Permission: " + permissions[i] + " was denied.", 
                                        MainViewModel.LogEntry.Type.ERROR);
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            // Initialize ViewModel
            viewModel = new ViewModelProvider(this).get(MainViewModel.class);

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
            initializeViews();
            setupObservers();

            viewModel.addLogEntry(getString(R.string.started_main_activity), MainViewModel.LogEntry.Type.INFO);
            viewModel.loadStatistics();
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate: " + e.getMessage(), e);
            // Show error to user
            Toast.makeText(this, "Failed to initialize app: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void initializeViews() {
        // Setup toolbar
        MaterialToolbar toolbar = findViewById(R.id.app_toolbar);
        setSupportActionBar(toolbar);

        // Initialize views
        storeCountTextView = findViewById(R.id.storeCountTextView);
        sentCountTextView = findViewById(R.id.sentCountTextView);
        unsentCountTextView = findViewById(R.id.unsentCountTextView);
        progressBar = findViewById(R.id.progressBar);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        // Setup RecyclerView
        RecyclerView logRecyclerView = findViewById(R.id.logRecyclerView);
        logAdapter = new LogAdapter(this);
        logRecyclerView.setAdapter(logAdapter);
        logRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Setup SwipeRefreshLayout
        swipeRefreshLayout.setOnRefreshListener(() -> viewModel.refreshLogs());

        // Setup buttons
        MaterialButton syncButton = findViewById(R.id.syncButton);
        MaterialButton clearCacheButton = findViewById(R.id.clearCacheButton);

        syncButton.setOnClickListener(v -> syncSms());
        clearCacheButton.setOnClickListener(v -> showClearCacheDialog());
    }

    private void setupObservers() {
        // Observe logs
        viewModel.getLogs().observe(this, logs -> logAdapter.updateLogs(logs));

        // Observe statistics
        viewModel.getStatistics().observe(this, stats -> {
            storeCountTextView.setText(String.valueOf(stats.totalCount));
            sentCountTextView.setText(String.valueOf(stats.sentCount));
            unsentCountTextView.setText(String.valueOf(stats.unsentCount));
            progressBar.setProgress(stats.progress);
        });

        // Observe loading state
        viewModel.getIsLoading().observe(this, isLoading -> 
            swipeRefreshLayout.setRefreshing(isLoading)
        );

        // Observe error messages
        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                viewModel.addLogEntry(error, MainViewModel.LogEntry.Type.ERROR);
            }
        });
    }

    private void syncSms() {
        viewModel.addLogEntry("Starting SMS sync...", MainViewModel.LogEntry.Type.INFO);
        
        // Reset watermark to zero
        MainApplication app = (MainApplication) getApplication();
        app.setWatermark(0);
        viewModel.loadStatistics();

        // Run store worker
        Handler handler = new Handler(Looper.getMainLooper());
        Context ctx = getApplicationContext();
        handler.post(new SmsStoreWorkerRunnable(ctx));
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
            syncSms();
            return true;
        }

        if (id == R.id.action_clear_cache) {
            viewModel.clearCache();
            return true;
        }

        if (id == R.id.action_quit) {
            finishAndRemoveTask();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // Legacy methods for backward compatibility with MainApplication
    public void restoreMessages() {
        // No longer needed with ViewModel approach
    }

    public void addMessage(String line) {
        if (line.contains("ERROR")) {
            viewModel.addLogEntry(line, MainViewModel.LogEntry.Type.ERROR);
        } else if (line.contains("WARN")) {
            viewModel.addLogEntry(line, MainViewModel.LogEntry.Type.WARNING);
        } else if (line.contains("SUCCESS") || line.contains("Uploaded")) {
            viewModel.addLogEntry(line, MainViewModel.LogEntry.Type.SUCCESS);
        } else {
            viewModel.addLogEntry(line, MainViewModel.LogEntry.Type.INFO);
        }
    }

    public void updateStats(Context ctx) {
        viewModel.loadStatistics();
    }
    
    private void showClearCacheDialog() {
        new MaterialAlertDialogBuilder(this)
            .setTitle("Clear Cache")
            .setMessage("This will clear all cached message data. You may want to sync SMS first to ensure all messages are uploaded.")
            .setPositiveButton("Clear All", (dialog, which) -> {
                viewModel.clearCache();
            })
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Fix Duplicates", (dialog, which) -> {
                viewModel.addLogEntry("Fixing duplicate entries...", MainViewModel.LogEntry.Type.INFO);
                // The migration will automatically fix duplicates when the database is upgraded
                viewModel.loadStatistics();
                Toast.makeText(this, "Database migration will fix duplicates on next app restart", Toast.LENGTH_LONG).show();
            })
            .show();
    }
}