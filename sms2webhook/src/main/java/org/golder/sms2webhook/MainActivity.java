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
import androidx.core.content.ContextCompat;
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
    private TextView statusHeadline;
    private TextView statusDetail;
    private MaterialButton syncButton;
    private MaterialButton stopSyncButton;

    private final Map<UUID, WorkInfo.State> workStates = new HashMap<>();

    /** Latest sync percentage, so the status line can render without re-querying. */
    private int syncProgress = 0;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != PERMISSION_REQUEST_CODE) {
            return;
        }
        boolean allGranted = grantResults.length > 0;
        for (int i = 0; i < permissions.length; i++) {
            if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                allGranted = false;
                Log.e(TAG, "Permission: " + permissions[i] + " was denied.");
                viewModel.addLogEntry("ERROR: Permission: " + permissions[i] + " was denied.",
                        MainViewModel.LogEntry.Type.ERROR);
            }
        }
        if (!allGranted) {
            // The UI is already built, so the reason is visible and the user can
            // reach Settings. Previously this left no window content at all.
            viewModel.addLogEntry(getString(R.string.permissions_required_toast),
                    MainViewModel.LogEntry.Type.WARNING);
        }
        viewModel.loadStatistics();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            viewModel = new ViewModelProvider(this).get(MainViewModel.class);

            MainApplication mainApplication = (MainApplication) getApplication();
            mainApplication.setMainActivity(this);

            // Built unconditionally. Gating this on the SMS permissions left a
            // blank window when they were denied, with no toolbar, no settings and
            // no way to ask again from inside the app.
            initializeUI();

            requestSmsPermissionsIfNeeded();
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate: " + e.getMessage(), e);
            Toast.makeText(this, "Failed to initialize app: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean hasSmsPermissions() {
        return checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestSmsPermissionsIfNeeded() {
        if (hasSmsPermissions()) {
            return;
        }
        viewModel.addLogEntry(getString(R.string.permissions_required_toast),
                MainViewModel.LogEntry.Type.WARNING);
        requestPermissions(
                new String[]{
                        Manifest.permission.RECEIVE_SMS,
                        Manifest.permission.READ_SMS
                },
                PERMISSION_REQUEST_CODE
        );
    }

    private void initializeUI() {
        setContentView(R.layout.activity_main);
        setupEdgeToEdge();
        initializeViews();
        setupObservers();
        viewModel.addLogEntry(getString(R.string.started_main_activity), MainViewModel.LogEntry.Type.INFO);
        viewModel.loadStatistics();
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

        statusHeadline = findViewById(R.id.statusHeadline);
        statusDetail = findViewById(R.id.statusDetail);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        logRecyclerView = findViewById(R.id.logRecyclerView);
        logAdapter = new LogAdapter(this);
        logRecyclerView.setAdapter(logAdapter);
        logRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        swipeRefreshLayout.setOnRefreshListener(() -> viewModel.refreshLogs());

        syncButton = findViewById(R.id.syncButton);
        stopSyncButton = findViewById(R.id.stopSyncButton);

        syncButton.setOnClickListener(v -> syncSms());
        stopSyncButton.setOnClickListener(v -> stopSync());
    }

    private void setupObservers() {
        // Scroll to top so newest entries are always visible
        viewModel.getLogs().observe(this, logs -> {
            logAdapter.updateLogs(logs);
            if (!logs.isEmpty()) {
                logRecyclerView.scrollToPosition(0);
            }
        });

        viewModel.getStatus().observe(this, status -> {
            if (status != null) {
                renderStatus(status);
            }
        });

        // Inline progress on the button, so a long sync still reports where it is
        // without spending a whole card on a progress bar.
        viewModel.getSyncProgress().observe(this, progress -> {
            if (progress == null) {
                return;
            }
            SyncStatus current = viewModel.getStatus().getValue();
            syncProgress = progress;
            if (current != null) {
                renderStatus(current);
            }
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
                // Cleared once shown. Left set, LiveData replays it to every new
                // observer, so rotating the device re-toasted the same error.
                viewModel.clearError();
            }
        });

        // Surface WorkManager state transitions so the user can see when the worker
        // is waiting for network, running, or has failed.
        WorkManager.getInstance(this).getWorkInfosByTagLiveData(SmsStoreWorker.WORK_TAG)
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

    /**
     * Draws one state on the status card and the button.
     *
     * <p>The headline is coloured rather than filling the card: a full red block on
     * an ordinary day because nothing has been synced yet is an alarm that gets
     * learned to ignore. Colour plus text is unambiguous and keeps the contrast.
     */
    private void renderStatus(SyncStatus status) {
        boolean syncing = status.state == SyncStatus.State.SYNCING;
        statusHeadline.setText(syncing
                ? getString(R.string.syncing_percent, status.percent)
                : status.headline());
        statusHeadline.setTextColor(ContextCompat.getColor(this, headlineColour(status.state)));

        String detail = status.detail();
        statusDetail.setText(detail == null ? "" : detail);
        statusDetail.setVisibility(detail == null ? View.GONE : View.VISIBLE);
    }

    private int headlineColour(SyncStatus.State state) {
        switch (state) {
            case REFUSED:
                return R.color.error;
            case UNSYNCED:
                return R.color.warning;
            case SYNCED:
                return R.color.success;
            case CANNOT_READ:
                return R.color.warning;
            case SYNCING:
            default:
                return R.color.info;
        }
    }

    private void syncSms() {
        viewModel.addLogEntry(getString(R.string.starting_sms_sync), MainViewModel.LogEntry.Type.INFO);

        // Rewind the saved sync position so the queued run rescans the inbox from
        // the start. Anything already delivered is recognised from the cache, so
        // this re-reads messages rather than re-sending them.
        SmsStoreWorker.requestFullRescan(this);
        viewModel.loadStatistics();

        Handler handler = new Handler(Looper.getMainLooper());
        Context ctx = getApplicationContext();
        handler.post(new SmsStoreWorkerRunnable(ctx));
    }

    private void stopSync() {
        viewModel.addLogEntry(getString(R.string.stopping_sync), MainViewModel.LogEntry.Type.WARNING);
        // Cancels the unique chain, which also drops any run still queued behind
        // the one in flight. Cancelling by tag would leave those queued to start
        // after the cancellation and continue the sync the user just stopped.
        WorkManager.getInstance(this).cancelUniqueWork(SmsStoreWorker.UNIQUE_WORK_NAME);
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
            // Through the dialog. The menu item used to clear the cache outright,
            // which is the one action on this screen that cannot be undone.
            showClearCacheDialog();
            return true;
        }

        if (id == R.id.action_quit) {
            finishAndRemoveTask();
            return true;
        }

        return super.onOptionsItemSelected(item);
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

    public void refreshStats(boolean withDiagnostics) {
        viewModel.loadStatistics(withDiagnostics);
    }

    /** Progress reported by the sync worker, drawn without touching the database. */
    public void reportSyncProgress(int processed, int total) {
        viewModel.reportSyncProgress(processed, total);
    }

    /**
     * Releases the application-held reference.
     *
     * <p>{@link MainApplication} lives for the whole process and posts work to the
     * main looper, so leaving this reference set kept a destroyed activity, its
     * view tree and its ViewModel reachable.
     */
    @Override
    protected void onDestroy() {
        MainApplication app = (MainApplication) getApplication();
        if (app != null) {
            app.setMainActivity(null);
        }
        super.onDestroy();
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
