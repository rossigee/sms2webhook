package org.golder.sms2webhook;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

public class SmsBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = SmsBroadcastReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            Log.w(TAG, "Not handling intent action: " + intent.getAction());
            return;
        }

        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null || messages.length == 0) {
            Log.w(TAG, "No SMS messages found in intent.");
            return;
        }

        Log.d(TAG, "Received " + messages.length + " SMS message(s)");

        String sender = messages[0].getDisplayOriginatingAddress();

        MainApplication app = (MainApplication)context.getApplicationContext();
        app.addMessage(context.getString(R.string.received_sms_from_s, sender));

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new SmsStoreWorkerRunnable(context));
    }
}