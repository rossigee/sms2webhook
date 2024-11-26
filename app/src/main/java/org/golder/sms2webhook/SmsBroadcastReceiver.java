package org.golder.sms2webhook;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsMessage;
import android.util.Log;

import static android.provider.Settings.System.getString;

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
        Log.d(TAG, "Incoming message bundle in '" + format + "' format.");

        // Print all fields of the bundle
//        for (String key: bundle.keySet()) {
//            String value = (bundle.get(key)).toString();
//            Log.d (TAG, "bundle(" + key + "): " + value);
//        }

        // Determine sender
        SmsMessage sms = SmsMessage.createFromPdu((byte[])pdus[0], format);
        String sender = sms.getDisplayOriginatingAddress();

        // Log receipt in activity view
        MainApplication app = (MainApplication)context.getApplicationContext();
        app.addMessage(context.getString(R.string.received_sms_from_s, sender));

        // Get the Handler instance from the main thread
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new SmsStoreWorkerRunnable(context));
    }
}