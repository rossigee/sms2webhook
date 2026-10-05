package org.golder.sms2webhook;

import android.app.Application;
import android.database.Cursor;
import android.provider.Telephony;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainViewModel extends AndroidViewModel {
    private static final String TAG = MainViewModel.class.getSimpleName();

    /** Ceiling on the retained activity log, so a long sync cannot grow it forever. */
    private static final int MAX_LOG_ENTRIES = 1000;

    private final MutableLiveData<List<LogEntry>> logs = new MutableLiveData<>();
    private final MutableLiveData<Statistics> statistics = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isSyncing = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Integer> syncProgress = new MutableLiveData<>(0);

    /**
     * Newest entry first.
     *
     * <p>Guarded by {@link #logLock} because it is written from the main thread and
     * from {@link #backgroundExecutor} threads at the same time: the diagnostics run
     * inside a statistics load and append entries of their own. Mutating and copying
     * an unsynchronised ArrayList from two threads throws, which is how a sync that
     * reported a finding could take the app down.
     */
    private final List<LogEntry> logList = new ArrayList<>();
    private final Object logLock = new Object();

    private final Executor backgroundExecutor = Executors.newFixedThreadPool(2);
    private final CacheDatabase cacheDatabase;

    public MainViewModel(@NonNull Application application) {
        super(application);
        cacheDatabase = CacheDatabase.getInstance(application);
        statistics.setValue(new Statistics(0, 0, 0));
        logs.setValue(new ArrayList<>());
        isLoading.setValue(false);
        isSyncing.setValue(false);
    }

    public LiveData<List<LogEntry>> getLogs() {
        return logs;
    }

    public LiveData<Statistics> getStatistics() {
        return statistics;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<Boolean> getIsSyncing() {
        return isSyncing;
    }

    /** Full refresh including diagnostics, for UI-initiated loads. */
    public void loadStatistics() {
        loadStatistics(true);
    }

    /**
     * Position within the current sync, as a percentage.
     *
     * <p>Reported straight from the worker rather than derived from the cached
     * counts. Deriving it from sentCount meant the bar only moved when the cache
     * was written, so it sat still for most of a sync and then jumped.
     */
    public LiveData<Integer> getSyncProgress() {
        return syncProgress;
    }

    /** Called by the worker after each message. Cheap: no database access. */
    public void reportSyncProgress(int processed, int total) {
        int percent = total > 0 ? Math.min(100, (processed * 100) / total) : 0;
        syncProgress.postValue(percent);
    }

    public void setSyncing(boolean syncing) {
        isSyncing.postValue(syncing);
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void addLogEntry(String message, LogEntry.Type type) {
        LogEntry entry = new LogEntry(message, type, System.currentTimeMillis());

        List<LogEntry> snapshot;
        synchronized (logLock) {
            logList.add(0, entry); // Add to top
            snapshot = snapshotOf(logList);
        }

        logs.postValue(snapshot);
    }

    /**
     * A copy of the log, newest first, capped at {@link #MAX_LOG_ENTRIES}.
     *
     * <p>Copies rather than exposing {@code logList}, which has to be copied anyway:
     * {@code ArrayList.toArray} walks the backing array by index, so taking one from
     * a list another thread is inserting into throws. The cap drops the oldest
     * entries, so a long sync cannot grow the log without bound.
     *
     * <p>Must be called while holding {@link #logLock}.
     */
    static List<LogEntry> snapshotOf(List<LogEntry> logList) {
        int size = Math.min(logList.size(), MAX_LOG_ENTRIES);
        return new ArrayList<>(logList.subList(0, size));
    }

    /**
     * Called once the current error has been shown, so a rotation does not replay
     * it. Not called from the background task that raises it: two postValue calls
     * in a row coalesce, so the error would never be delivered at all.
     */
    public void clearError() {
        errorMessage.setValue(null);
    }

    /**
     * Refreshes the cached counts.
     *
     * @param withDiagnostics run the consistency checks. Off for the periodic
     *        refresh during a sync: diagnostics append a log entry each time
     *        they find something, so a sync refreshing every few dozen messages
     *        filled the activity log with the same finding repeated.
     */
    public void loadStatistics(boolean withDiagnostics) {
        // postValue rather than setValue: this method is also called from
        // clearCache(), which runs on backgroundExecutor. setValue on a
        // background thread throws, which is why clearing the cache silently
        // failed with "Cannot invoke setValue on a background thread".
        isLoading.postValue(true);
        backgroundExecutor.execute(() -> {
            try {
                // Queried here rather than read from fields cached on the
                // Application object. Those were populated once at process start and
                // never written again, so the dashboard showed the same three numbers
                // for the whole life of the process no matter how much was uploaded,
                // and "Clear cache" appeared to do nothing.
                int totalCount = countMessages();
                int sentCount = cacheDatabase.cacheDao().getSent();
                int unsentCount = cacheDatabase.cacheDao().getNotSent();

                if (withDiagnostics) {
                    runDiagnostics(totalCount, sentCount, unsentCount);
                }

                Statistics stats = new Statistics(totalCount, sentCount, unsentCount);
                statistics.postValue(stats);
                isLoading.postValue(false);
            } catch (Exception e) {
                errorMessage.postValue("Failed to load statistics: " + e.getMessage());
                isLoading.postValue(false);
            }
        });
    }

    /**
     * How many messages the provider is holding.
     *
     * <p>Returns 0 rather than throwing when the SMS permission has been refused,
     * so a dashboard load on a device without permission still renders.
     */
    private int countMessages() {
        try (Cursor cursor = getApplication().getContentResolver().query(
                Telephony.Sms.CONTENT_URI, null, null, null, "_id")) {
            return cursor == null ? 0 : cursor.getCount();
        } catch (Exception e) {
            Log.w(TAG, "Could not count messages: " + e.getMessage());
            return 0;
        }
    }

    private void runDiagnostics(int storeCount, int sentCount, int unsentCount) {
        try {
            int uniqueCount = cacheDatabase.cacheDao().getUniqueCount();
            int totalCacheEntries = cacheDatabase.cacheDao().getTotalCount();

            // Check for duplicates
            if (totalCacheEntries > uniqueCount) {
                int duplicates = totalCacheEntries - uniqueCount;
                addLogEntry("⚠️ Found " + duplicates + " duplicate cache entries", LogEntry.Type.WARNING);
            }

            // Check for missing entries
            if (storeCount > uniqueCount) {
                int missing = storeCount - uniqueCount;
                addLogEntry("ℹ️ " + missing + " messages have no cache entry", LogEntry.Type.INFO);
            }

            // Check for inconsistencies
            int accountedFor = sentCount + unsentCount;
            if (accountedFor != uniqueCount && totalCacheEntries == uniqueCount) {
                addLogEntry("⚠️ Cache inconsistency detected: " + uniqueCount + " unique messages, but " +
                           sentCount + " sent + " + unsentCount + " unsent", LogEntry.Type.WARNING);
            }
        } catch (Exception e) {
            // Diagnostics failed, but don't break the app
            Log.e(TAG, "Diagnostics failed", e);
        }
    }

    public void refreshLogs() {
        isLoading.setValue(true);
        // Simulate refresh delay
        backgroundExecutor.execute(() -> {
            try {
                Thread.sleep(500); // Brief delay for UX
                isLoading.postValue(false);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                isLoading.postValue(false);
            }
        });
    }

    public void clearCache() {
        isLoading.setValue(true);
        backgroundExecutor.execute(() -> {
            try {
                cacheDatabase.cacheDao().clear();
                addLogEntry("Cache cleared successfully", LogEntry.Type.SUCCESS);
                loadStatistics();
                isLoading.postValue(false);
            } catch (Exception e) {
                errorMessage.postValue("Failed to clear cache: " + e.getMessage());
                isLoading.postValue(false);
            }
        });
    }

    public static class Statistics {
        public final int totalCount;
        public final int sentCount;
        public final int unsentCount;
        public final int progress;

        public Statistics(int totalCount, int sentCount, int unsentCount) {
            this.totalCount = totalCount;
            this.sentCount = sentCount;
            this.unsentCount = unsentCount;
            this.progress = totalCount > 0 ? Math.min(100, (sentCount * 100) / totalCount) : 0;
        }
    }

    public static class LogEntry {
        public final String message;
        public final Type type;
        public final long timestamp;

        public LogEntry(String message, Type type, long timestamp) {
            this.message = message;
            this.type = type;
            this.timestamp = timestamp;
        }

        public enum Type {
            INFO, SUCCESS, WARNING, ERROR
        }
    }
}