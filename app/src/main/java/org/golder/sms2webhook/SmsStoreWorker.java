package org.golder.sms2webhook;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;
// import android.widget.Toast;

import org.golder.sms2webhook.AlreadyExistsException;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class SmsStoreWorker extends Worker {
    private final DigestCache cache;

    SmsUploadService service;
    private boolean isBound = false;

    public SmsStoreWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);

        cache = DigestCache.getInstance();
    }

    private final ServiceConnection connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder binder) {
            service = ((SmsUploadService.LocalBinder)binder).getService();
            Log.d("worker", "Worker connected to service");
        }

        public void onServiceDisconnected(ComponentName className) {
            service = null;
            Log.d("worker", "Worker disconnected from service");
        }
    };

    protected void onStart() {
        Intent intent = new Intent(getApplicationContext(), SmsUploadService.class);
        isBound = getApplicationContext().bindService(intent, connection, Context.BIND_AUTO_CREATE);
        if(!isBound) {
            Log.e("worker", "Unable to bind to service.");
        }
    }

    protected void onStop() {
        if (isBound) {
            getApplicationContext().unbindService(connection);
            isBound = false;
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        onStart();

        // Check for credentials first
        SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String webhookUrl = prefs.getString("webhook_url", "");
        if(webhookUrl.isEmpty()) {
            Log.e("worker", "Webhook URL is empty. Please configure in settings.");
            service.setStatus("Webhook URL is empty. Please configure in settings.\n");
            return Result.failure();
        }

        // Find SMSs already on phone, send for processing
        ContentResolver resolver = getApplicationContext().getContentResolver();
        Cursor cursor = resolver.query(Uri.parse("content://sms/inbox"), null, null, null, null);
        if (cursor == null) {
            Log.e("worker", "Unable to obtain SMS cursor.");
            service.setStatus("Unable to obtain SMS cursor.");
            return Result.failure();
        }
        if (!cursor.moveToFirst()) {
            Log.e("worker", "No SMS messages found in inbox on phone.");
            service.setStatus("No SMS messages found in inbox on phone.\n");
            return Result.failure();
        }
        service.setInboxCount(cursor.getCount());
        service.setStatus("Found " + cursor.getCount() + " messages in SMS inbox.\n");

        // Now, just focus on those since the last run (using watermark)
        int count = 0;
        do {
            JSONObject msg = encodeMessage(cursor);
            String sender = "N/A";
            try {
                sender = msg.getString("address");
            } catch (JSONException je) {
                Log.w("worker", "Error fetching sender: " + je);
            }

            //Log.d("worker", msg.toString());

            // Calculate message hash to use as hash key
            String objectname;
            try {
                String[] hashableitems = new String[3];
                hashableitems[0] = String.valueOf(msg.getLong("date"));
                hashableitems[1] = msg.getString("address");
                hashableitems[2] = msg.getString("body");
                String hashable = StringUtils.join(hashableitems);
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(hashable.getBytes(StandardCharsets.UTF_8));
                objectname = bytesToHex(hash);
            }
            catch(JSONException je) {
                Log.e("worker", "Could not determine attributes of message to use for hash: " + je);
                service.setStatus("Message attributes error from '" + sender + "': " + je + "\n");
                continue;
            }
            catch(NoSuchAlgorithmException nsae) {
                Log.e("worker", nsae.toString());
                service.setStatus("Hashing error from '" + sender + "': " + nsae + "\n");
                continue;
            }

            // Look up objectname in cache
            if(!cache.exists(objectname)) {
                // Pass message to webhook
                try {
                    WebhookUploader.upload(msg, objectname, prefs);
                    service.setStatus("Processed message from '" + sender + "'\n");
                    cache.add(objectname);
                } catch (AlreadyExistsException aee) {
                    Log.i("worker", "Already exists: " + aee);
                    service.setStatus("Message already uploaded from '" + sender + "'\n");
                    cache.add(objectname);
                } catch (IllegalArgumentException iae) {
                    Log.e("worker", "Illegal argument: " + iae);
                    service.setStatus("Error processing message from '" + sender + "': " + iae + "\n");
                }
            }

            // Update progress
            count += 1;
            service.setProcessedCount(count);

        } while (cursor.moveToNext());

        // Flush cache to storage
        try {
            Log.i("worker", "Saving cache...");
            cache.save();
        }
        catch(IOException ioe) {
            Log.e("worker", "Unable to save cache: " + ioe);
        }

        return Result.success();
    }

    private JSONObject encodeMessage(Cursor cursor) {
        JSONObject msg = new JSONObject();
        for(int idx = 0; idx < cursor.getColumnCount(); idx++) {
            String colname = cursor.getColumnName(idx);
            String colval = cursor.getString(idx);
            try {
                if (colname.equals("date") || colname.equals("date_sent")) {
                    msg.put(colname, Long.valueOf(colval));
                } else {
                    msg.put(colname, colval);
                }
            }
            catch(JSONException e) {
                Log.e("worker", "Could not add '" + colval + "' to JSON object.");
            }
        }
        return msg;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) result.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        return result.toString();
    }


}
