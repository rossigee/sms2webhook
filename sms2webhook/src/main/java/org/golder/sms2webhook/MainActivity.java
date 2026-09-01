package org.golder.sms2webhook;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int PERMISSION_REQUEST_CODE = 1;

    private MainViewModel viewModel;
    private LogAdapter logAdapter;
    private RecyclerView logRecyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearProgressIndicator progressBar;
    private TextView storeCountTextView;
    private TextView sentCountTextView;
    private TextView unsentCountTextView;
    private MaterialButton syncButton;
    private MaterialButton stopSyncButton;

    private boolean uiInitialized = false;
    private final Map<UUID, WorkInfo.State> workStates = new HashMap<>();

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = grantResults.length > 0;
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    allGranted = false;
                    Log.e(TAG, "Permission: " + permissions[i] + " was denied.");
                    viewModel.addLogEntry("ERROR: Permission: " + permissions[i] + " was denied.",
                            MainViewModel.LogEntry.Type.ERROR);
                }
            }
            if (allGranted && !uiInitialized) {
                initializeUI();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            viewModel = new ViewModelProvider(this).get(MainViewModel.class);

            MainApplication mainApplication = (MainApplication) getApplication();
            mainApplication.setMainActivity(this);

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

            initializeUI();
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate: " + e.getMessage(), e);
            Toast.makeText(this, "Failed to initialize app: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void initializeUI() {
        setContentView(R.layout.activity_main);
        setupEdgeToEdge();
        initializeViews();
        setupObservers();
        viewModel.addLogEntry(getString(R.string.started_main_activity), MainViewModel.LogEntry.Type.INFO);
        viewModel.loadStatistics();
        uiInitialized = true;
    }

    private void setupEdgeToEdge() {
        MaterialToolbar toolbar = findViewById(R.id.app_toolbar);
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), insets.top, v.getPaddingRight(), v.getPaddingBottom());
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void initializeViews() {
        MaterialToolbar toolbar = findViewById(R.id.app_toolbar);
        setSupportActionBar(toolbar);

        storeCountTextView = findViewById(R.id.storeCountTextView);
        sentCountTextView = findViewById(R.id.sentCountTextView);
        unsentCountTextView = findViewById(R.id.unsentCountTextView);
        progressBar = findViewById(R.id.progressBar);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        logRecyclerView = findViewById(R.id.logRecyclerView);
        logAdapter = new LogAdapter(this);
        logRecyclerView.setAdapter(logAdapter);
        logRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        swipeRefreshLayout.setOnRefreshListener(() -> viewModel.refreshLogs());

        syncButton = findViewById(R.id.syncButton);
        stopSyncButton = findViewById(R.id.stopSyncButton);
        MaterialButton clearCacheButton = findViewById(R.id.clearCacheButton);

        syncButton.setOnClickListener(v -> syncSms());
        stopSyncButton.setOnClickListener(v -> stopSync());
        clearCacheButton.setOnClickListener(v -> showClearCacheDialog());
    }

    private void setupObservers() {
        // Scroll to top so newest entries are always visible
        viewModel.getLogs().observe(this, logs -> {
            logAdapter.updateLogs(logs);
            if (!logs.isEmpty()) {
                logRecyclerView.scrollToPosition(0);
            }
        });

        viewModel.getStatistics().observe(this, stats -> {
            storeCountTextView.setText(String.valueOf(stats.totalCount));
            sentCountTextView.setText(String.valueOf(stats.sentCount));
            unsentCountTextView.setText(String.valueOf(stats.unsentCount));
            progressBar.setProgress(stats.progress);
        });

        viewModel.getIsLoading().observe(this, isLoading ->
            swipeRefreshLayout.setRefreshing(isLoading)
        );

        // Observe syncing state to show/hide buttons
        viewModel.getIsSyncing().observe(this, isSyncing -> {
            if (isSyncing != null) {
                updateSyncButtons(isSyncing);
            }
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                viewModel.addLogEntry(error, MainViewModel.LogEntry.Type.ERROR);
            }
        });

        // Surface WorkManager state transitions so the user can see when the worker
        // is waiting for network, running, or has failed.
        WorkManager.getInstance(this).getWorkInfosByTagLiveData("message")
                .observe(this, workInfos -> {
                    if (workInfos == null) return;
                    boolean anyRunning = false;
                    for (WorkInfo info : workInfos) {
                        WorkInfo.State prev = workStates.get(info.getId());
                        WorkInfo.State curr = info.getState();
                        if (curr == prev) continue;
                        workStates.put(info.getId(), curr);
                        switch (curr) {
                            case RUNNING:
                                anyRunning = true;
                                viewModel.addLogEntry(getString(R.string.worker_running),
                                        MainViewModel.LogEntry.Type.INFO);
                                break;
                            case SUCCEEDED:
                            case FAILED:
                            case CANCELLED:
                                viewModel.setSyncing(false);
                                break;
                            default:
                                break;
                        }
                    }
                    // If any work is running, we're syncing
                    if (anyRunning) {
                        viewModel.setSyncing(true);
                    }
                });
    }

    private void syncSms() {
        viewModel.addLogEntry(getString(R.string.starting_sms_sync), MainViewModel.LogEntry.Type.INFO);

        MainApplication app = (MainApplication) getApplication();
        app.setWatermark(0);
        viewModel.loadStatistics();

        Handler handler = new Handler(Looper.getMainLooper());
        Context ctx = getApplicationContext();
        handler.post(new SmsStoreWorkerRunnable(ctx));
    }

    private void stopSync() {
        viewModel.addLogEntry(getString(R.string.stopping_sync), MainViewModel.LogEntry.Type.WARNING);
        WorkManager.getInstance(this).cancelAllWorkByTag("message");
        viewModel.setSyncing(false);
    }

    private void updateSyncButtons(boolean isSyncing) {
        if (isSyncing) {
            syncButton.setVisibility(View.GONE);
            stopSyncButton.setVisibility(View.VISIBLE);
        } else {
            syncButton.setVisibility(View.VISIBLE);
            stopSyncButton.setVisibility(View.GONE);
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
            startActivity(new Intent(this, SettingsActivity.class));
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
            .setTitle(R.string.clear_cache)
            .setMessage(R.string.clear_cache_message)
            .setPositiveButton(R.string.clear_all, (dialog, which) -> viewModel.clearCache())
            .setNegativeButton(R.string.cancel, null)
            .show();
    }
}
