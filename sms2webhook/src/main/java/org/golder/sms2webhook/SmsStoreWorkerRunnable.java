package org.golder.sms2webhook;

import android.content.Context;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
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
                .addTag(SmsStoreWorker.WORK_TAG)
                .setInputData(data.build())
                .setConstraints(builder.build())
                .build();

        // Enqueued as unique work rather than as an independent request. Every
        // incoming SMS asks for a sync and the user can start one from the toolbar,
        // so as independent requests WorkManager ran them in parallel and they each
        // walked the same messages, uploading duplicates.
        //
        // APPEND_OR_REPLACE rather than KEEP: a message that arrives while a sync
        // is running lands past the point that run had already counted, so its
        // trigger has to survive for a follow-up run to pick it up. KEEP would
        // discard it and the message would sit unsent until the next sync of any
        // kind. REPLACE would instead cancel the run in flight and restart the
        // scan from its last saved position, making no forward progress if messages
        // keep arriving.
        instance.enqueueUniqueWork(
                SmsStoreWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request);

        MainApplication app = (MainApplication) context.getApplicationContext();
        app.addMessage(context.getString(R.string.sync_queued));
    }
}
