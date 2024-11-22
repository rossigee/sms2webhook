package org.golder.sms2webhook;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsMessage;
import android.util.Log;

import androidx.work.OneTimeWorkRequest;

public class SmsBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = SmsBroadcastReceiver.class.getSimpleName();
    private static final String ACTION_SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i(TAG, "Received intent action: " + action);

        if (!ACTION_SMS_RECEIVED.equals(action)) {
            Log.w(TAG, "Not handling intent action: " + action);
            return;
        }

        Bundle bundle = intent.getExtras();
        if (bundle == null) {
            Log.w(TAG, "No bundle data found.");
            return;
        }

        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null || pdus.length == 0) {
            Log.w(TAG, "No SMS PDUs found.");
            return;
        }

        String format = (String) bundle.get("format");
        Log.d(TAG, "Incoming message in '" + format + "' format.");

        // Get the Handler instance from the main thread
        Handler handler = new Handler(Looper.getMainLooper());
        for (int i = 0; i < pdus.length; i++) {
            byte[] pdu = (byte[]) pdus[i];
            handler.post(new WorkerRunnable(context, pdu, format));
        }
    }
}