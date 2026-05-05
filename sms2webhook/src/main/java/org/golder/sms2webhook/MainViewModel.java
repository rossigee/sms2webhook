package org.golder.sms2webhook;

import android.app.Application;
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
    private final MutableLiveData<List<LogEntry>> logs = new MutableLiveData<>();
    private final MutableLiveData<Statistics> statistics = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    
    private final List<LogEntry> logList = new ArrayList<>();
    private final Executor backgroundExecutor = Executors.newFixedThreadPool(2);
    private final CacheDatabase cacheDatabase;

    public MainViewModel(@NonNull Application application) {
        super(application);
        cacheDatabase = CacheDatabase.getInstance(application);
        statistics.setValue(new Statistics(0, 0, 0));
        logs.setValue(new ArrayList<>());
        isLoading.setValue(false);
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

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void addLogEntry(String message, LogEntry.Type type) {
        LogEntry entry = new LogEntry(message, type, System.currentTimeMillis());
        logList.add(0, entry); // Add to top
        
        // Limit log size to prevent memory issues
        if (logList.size() > 1000) {
            logList.remove(logList.size() - 1);
        }
        
        logs.postValue(new ArrayList<>(logList));
    }

    public void loadStatistics() {
        isLoading.setValue(true);
        backgroundExecutor.execute(() -> {
            try {
                MainApplication app = getApplication();
                int totalCount = app.inboxCount;
                int sentCount = app.sentCount;
                int unsentCount = app.unsentCount;
                
                // Run diagnostics to check for inconsistencies
                runDiagnostics();
                
                Statistics stats = new Statistics(totalCount, sentCount, unsentCount);
                statistics.postValue(stats);
                isLoading.postValue(false);
            } catch (Exception e) {
                errorMessage.postValue("Failed to load statistics: " + e.getMessage());
                isLoading.postValue(false);
            }
        });
    }
    
    private void runDiagnostics() {
        try {
            int uniqueCount = cacheDatabase.cacheDao().getUniqueCount();
            int totalCacheEntries = cacheDatabase.cacheDao().getTotalCount();
            int sentCount = cacheDatabase.cacheDao().getSent();
            int unsentCount = cacheDatabase.cacheDao().getNotSent();
            
            MainApplication app = getApplication();
            int storeCount = app.inboxCount;
            
            // Check for duplicates
            if (totalCacheEntries > uniqueCount) {
                int duplicates = totalCacheEntries - uniqueCount;
                addLogEntry("⚠️ Found " + duplicates + " duplicate cache entries", LogEntry.Type.WARNING);
            }
            
            // Check for missing entries
            int accountedFor = sentCount + unsentCount;
            if (storeCount > uniqueCount) {
                int missing = storeCount - uniqueCount;
                addLogEntry("ℹ️ " + missing + " messages have no cache entry", LogEntry.Type.INFO);
            }
            
            // Check for inconsistencies
            if (accountedFor != uniqueCount && totalCacheEntries == uniqueCount) {
                addLogEntry("⚠️ Cache inconsistency detected: " + uniqueCount + " unique messages, but " + 
                           sentCount + " sent + " + unsentCount + " unsent", LogEntry.Type.WARNING);
            }
        } catch (Exception e) {
            // Diagnostics failed, but don't break the app
            Log.e("MainViewModel", "Diagnostics failed", e);
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