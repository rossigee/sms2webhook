package org.golder.sms2webhook;

import android.content.Context;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

public class SmsStoreWorkerRunnable implements Runnable {
    private static final String TAG = SmsStoreWorkerRunnable.class.getSimpleName();

    private final Context context;

    public SmsStoreWorkerRunnable(Context ctx) {
        super();
        this.context = ctx;
    }

    @Override
    public void run() {
        WorkManager instance;
        try {
            instance = WorkManager.getInstance(context);
        } catch (IllegalStateException e) {
            Log.e(TAG, "WorkManager not initialised", e);
            return;
        }

        Log.i(TAG, "Running worker...");
        Constraints.Builder builder = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED);
        Data.Builder data = new Data.Builder();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SmsStoreWorker.class)
                .addTag("message")
                .setInputData(data.build())
                .setConstraints(builder.build())
                .build();
        instance.enqueue(request);

        MainApplication app = (MainApplication) context.getApplicationContext();
        app.addMessage(context.getString(R.string.sync_queued));
    }
}
