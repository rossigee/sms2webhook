package org.golder.sms2webhook;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

public class SmsUploadService extends IntentService {

    // Use a final constant for the service name
    public static final String SERVICE_NAME = "SmsUploadService";
    private final IBinder binder = new LocalBinder();

    private DigestCache cache;
    private Statistics stats;

    public SmsUploadService() {
        super(SERVICE_NAME);
    }

    @Override
    public void onCreate() {
        // Initialize cache and stats here to avoid lateinit warnings
        cache = DigestCache.getInstance();
        stats = Statistics.getInstance();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Use a string resource for toast messages
        Toast.makeText(this, getString(R.string.service_starting), Toast.LENGTH_SHORT).show();

        // Extract cache file name into a constant
        final String CACHE_FILE_NAME = "cache";
        cache.setFilename(getCacheDir().getAbsolutePath() + File.separator + CACHE_FILE_NAME);
        try {
            cache.load();
        } catch (IOException ioe) {
            Log.e("service", "Unable to load cache: " + ioe);
            // Use a string resource for toast messages
            Toast.makeText(this, getString(R.string.unable_to_load_cache), Toast.LENGTH_SHORT).show();
        }

        refresh();

        return START_REDELIVER_INTENT;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    protected void onHandleIntent(Intent intent) {
        Log.i("service", "Received intent: " + intent);
    }

    @Override
    public void onDestroy() {
        // Use a string resource for toast messages
        Toast.makeText(this, getString(R.string.service_done), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        // Stop the service when the user removes the task
        stopSelf();
    }

    public void clearCache() {
        Log.i("service", "Clearing cache...");
        cache.clear();
    }

    public void saveCache() {
        Log.i("service", "Saving cache...");
        try {
            cache.save();
        } catch (IOException ioe) {
            Log.e("service", "Unable to save cache: " + ioe);
            // Use a string resource for toast messages
            Toast.makeText(this, getString(R.string.unable_to_save_cache), Toast.LENGTH_SHORT).show();
        }
    }

    public void refresh() {
        // Process any messages already on phone
        Log.i("service", "Running SMS Store Worker...");

        OneTimeWorkRequest request = buildWorkRequest();
        WorkManager.getInstance(this).enqueue(request);
    }

    private OneTimeWorkRequest buildWorkRequest() {
        Constraints.Builder builder = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED);
        Data.Builder data = new Data.Builder();
        return new OneTimeWorkRequest.Builder(SmsStoreWorker.class)
                        .addTag("refresh")
                        .setInputData(data.build())
                        .setConstraints(builder.build())
                        .build();
    }

    public void setStatus(String status) {
        Log.i("service", "STATUS: " + status);
        new Handler(Looper.getMainLooper()).post(() -> stats.setStatus(status));
    }

    public void setInboxCount(int count) {
        stats.setInboxCount(count);
    }

    public void setProcessedCount(int count) {
        stats.setProcessedCount(count);
    }

    public class LocalBinder extends Binder {
        SmsUploadService getService() {
            return SmsUploadService.this;
        }
    }
}