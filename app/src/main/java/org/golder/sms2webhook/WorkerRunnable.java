package org.golder.sms2webhook;

import android.content.Context;
import android.telephony.SmsMessage;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

public class WorkerRunnable implements Runnable {
    private static final String TAG = WorkerRunnable.class.getSimpleName();

    private final Context context;
    private final byte[] pdu;
    private final String format;

    public WorkerRunnable(Context ctx, byte[] pdu, String format) {
        super();
        this.context = ctx;
        this.pdu = pdu;
        this.format = format;
    }

    @Override
    public void run() {
        WorkManager instance = WorkManager.getInstance(context);
        if (instance == null) {
            Log.e(TAG, "WorkManager is null");
            return;
        }

        // Start periodic worker to process SMS store
        Log.i(TAG, "Running worker...");
        Constraints.Builder builder = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED);
        Data.Builder data = new Data.Builder()
                .putByteArray("pdu", pdu)
                .putString("format", format);
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SmsStoreWorker.class)
                .addTag("message")
                .setInputData(data.build())
                .setConstraints(builder.build())
                .build();
        instance.enqueue(request);
    }
}
