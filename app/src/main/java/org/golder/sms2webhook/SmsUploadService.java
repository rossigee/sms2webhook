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
    // private Context context;

    private final IBinder binder = new LocalBinder();

    // private SmsStoreWorker worker;
    private DigestCache cache;
    private Statistics stats;

    public SmsUploadService() {
        super("SmsUploadService");
    }

    @Override
    public void onCreate() {
        cache = DigestCache.getInstance();
        stats = Statistics.getInstance();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, "service starting", Toast.LENGTH_SHORT).show();

        String cachefilename = getCacheDir().getAbsolutePath() + File.separator + "cache";
        cache.setFilename(cachefilename);
        try {
            cache.load();
        }
        catch(IOException ioe) {
            Toast.makeText(this, "unable to load cache", Toast.LENGTH_SHORT).show();
            Log.e("service", "Unable to save cache: " + ioe);
        }

        refresh();

        return START_REDELIVER_INTENT;
    }

    public class LocalBinder extends Binder {
        SmsUploadService getService() {
            return SmsUploadService.this;
        }
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
        Toast.makeText(this, "service done", Toast.LENGTH_SHORT).show();
    }

    public void clear_cache() {
        Log.i("service", "Clearing cache...");
        cache.clear();
    }

    public void save_cache() {
        Log.i("service", "Saving cache...");
        try {
            cache.save();
        }
        catch(IOException ioe) {
            Toast.makeText(this, "unable to save cache", Toast.LENGTH_SHORT).show();
            Log.e("service", "Unable to save cache: " + ioe);
        }
    }

    public void refresh() {
        // Process any messages already on phone
        Log.i("service", "Running SMS Store Worker...");

        Constraints.Builder builder = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED);
        Data.Builder data = new Data.Builder();
        OneTimeWorkRequest request =
                new OneTimeWorkRequest.Builder(SmsStoreWorker.class)
                        .addTag("refresh")
                        .setInputData(data.build())
                        .setConstraints(builder.build())
                        .build();

        Context context = getApplicationContext();
        WorkManager manager = WorkManager.getInstance(context);
        manager.enqueue(request);
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
}
