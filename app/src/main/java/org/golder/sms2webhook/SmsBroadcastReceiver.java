package org.golder.sms2webhook;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;


public class SmsBroadcastReceiver extends BroadcastReceiver {
    SmsUploadService service;
    private final boolean isBound = false;

    Context context;

    private final ServiceConnection connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder binder) {
            service = ((SmsUploadService.LocalBinder)binder).getService();
            Log.d("receiver", "Receiver connected to service");

            // Issue a refresh and unbind
            service.refresh();
        }

        public void onServiceDisconnected(ComponentName className) {
            service = null;
            Log.d("receiver", "Receiver disconnected from service");
        }
    };

    @Override
    public void onReceive(Context context, Intent intent) {
        this.context = context;

        Log.i("receiver", "Handling event " + intent.getAction());
        if (!intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED")) {
            return;
        }

        Bundle bundle = intent.getExtras();
        if (bundle == null) {
            Log.w("receiver", "No data found.");
            return;
        }

        // Report on messages arriving.
        SmsMessage[] messages;
        String msgFrom = "N/A";
        try {
            Object[] pdus = (Object[]) bundle.get("pdus");
            String format = (String)bundle.get("format");
            messages = new SmsMessage[pdus.length];
            for (int i = 0; i < messages.length; i++) {
                messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i], format);
                msgFrom = messages[i].getOriginatingAddress();
                Log.i("receiver", "Handling SMS from '" + msgFrom + "'");
                Toast.makeText(context, "Handling SMS from '" + msgFrom + "'", Toast.LENGTH_SHORT).show();
                String msgBody = messages[i].getMessageBody();
                Log.d("receiver", msgBody);
            }
        }
        catch (Exception e) {
            Log.e("receiver", e.toString());
        }

        // Wait a sec, just to be sure SMS has a chance to arrive in Inbox (needed?)
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Connect to service
        Log.i("receiver", "Connecting to service...");
        Intent serviceIntent = new Intent(context, SmsUploadService.class);
        context.startService(serviceIntent);
        IBinder binder = peekService(context, serviceIntent);
        if (binder == null) {
            Log.e("receiver", "Binder is null");
            return;
        }
        service = ((SmsUploadService.LocalBinder)binder).getService();
        Log.i("receiver", "Connected to service...");

        // Force collection of new messages in inbox
        service.setStatus("Received message from '" + msgFrom + "'\n");
        service.refresh();
    }
}