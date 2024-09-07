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

import static android.provider.Telephony.Sms.Intents.SMS_RECEIVED_ACTION;

public class SmsBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsBroadcastReceiver";
    private static final String ACTION_SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";
    private static final int SMS_DELAY_BEFORE_REFRESH = 2000; // 2 seconds
    private SmsUploadService service;
    private boolean isBound = false;
    private Context context;

    private final ServiceConnection connection = new ServiceConnection() {
    @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            service = ((SmsUploadService.LocalBinder) binder).getService();
            Log.d(TAG, "Receiver connected to service");
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            service = null;
            Log.d(TAG, "Receiver disconnected from service");
        }
    };

    @Override
    public void onReceive(Context context, Intent intent) {
        this.context = context;

        String action = intent.getAction();
        Log.i(TAG, "Received intent action: " + action);

        if (!ACTION_SMS_RECEIVED.equals(action)) {
            Log.w(TAG, "Not handling intent action: " + action);
            return;
        }

        Bundle bundle = intent.getExtras();
        if (bundle == null) {
            Log.w(TAG, "No data found.");
            return;
        }

        processIncomingSms(bundle);
    }

    private void processIncomingSms(Bundle bundle) {
            Object[] pdus = (Object[]) bundle.get("pdus");
        String format = (String) bundle.get("format");

        if (pdus == null || pdus.length == 0) {
            Log.w(TAG, "No SMS data found.");
            return;
        }

        for (int i = 0; i < pdus.length; i++) {
            SmsMessage message = SmsMessage.createFromPdu((byte[]) pdus[i], format);
            if (message == null) {
                Log.w(TAG, "Failed to create SMS message from PDU.");
                continue;
            }

            String msgFrom = message.getOriginatingAddress();
            String msgBody = message.getMessageBody();

            Log.i(TAG, "Received SMS from '" + msgFrom + "'");
            Log.d(TAG, msgBody);
            Toast.makeText(context, "Received SMS from '" + msgFrom + "'", Toast.LENGTH_SHORT).show();

            // Wait a short delay before connecting to the service
            new Thread(() -> {
                try {
                    Thread.sleep(SMS_DELAY_BEFORE_REFRESH);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Failed to delay SMS processing", e);
                }

                // Connect to service
                Log.i(TAG, "Connecting to service...");
                Intent serviceIntent = new Intent(context, SmsUploadService.class);
                context.startService(serviceIntent);

                // If the service is not already bound, bind it now
                if (!isBound) {
                    context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);
                    isBound = true;
                }
            }).start();
        }
    }
}