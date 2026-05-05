package org.golder.sms2webhook;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Handler;
import android.provider.Telephony;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.preference.PreferenceManager;

public class MainApplication extends Application {
    private MainActivity mainActivity;

    private final AtomicInteger watermark = new AtomicInteger(0);

    int inboxCount = 0;
    int sentCount = 0;
    int unsentCount = 0;

    private final ArrayList<String> messages = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();

        Context ctx = getApplicationContext();

        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
            watermark.set(prefs.getInt("watermark", 0));
        } catch (Exception e) {
            android.util.Log.e("MainApplication", "Failed to get preferences: " + e.getMessage());
            watermark.set(0);
        }

        new Thread(() -> {
            try {
                try (Cursor cursor = getContentResolver().query(Telephony.Sms.CONTENT_URI, null, null, null, "_id")) {
                    if (cursor != null) {
                        inboxCount = cursor.getCount();
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("MainApplication", "Failed to query SMS: " + e.getMessage());
                inboxCount = 0;
            }

            try {
                sentCount = DigestCache.getSentCount(ctx);
                unsentCount = DigestCache.getNotSentCount(ctx);
            } catch (Exception e) {
                android.util.Log.e("MainApplication", "Failed to get cache counts: " + e.getMessage());
                sentCount = 0;
                unsentCount = 0;
            }
        }).start();
    }

    public void setMainActivity(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }

    public String[] getMessages() {
        return messages.toArray(new String[0]);
    }

    public void setWatermark(int level) {
        watermark.set(level);
        updateStats();
    }

    public int getWatermark() {
        return watermark.get();
    }

    public void updateStats() {
        Context ctx = getApplicationContext();
        Handler handler = new Handler(ctx.getMainLooper());
        handler.post(() -> {
            if (mainActivity != null) {
                mainActivity.updateStats(ctx);
            }
        });
    }

    public void restoreMessages() {
        Context ctx = getApplicationContext();
        Handler handler = new Handler(ctx.getMainLooper());
        handler.post(() -> {
            if (mainActivity != null) {
                mainActivity.restoreMessages();
            }
        });
    }

    public void addMessage(String line) {
        messages.add(line);
        Context ctx = getApplicationContext();
        Handler handler = new Handler(ctx.getMainLooper());
        handler.post(() -> {
            if (mainActivity != null) {
                mainActivity.addMessage(line);
            }
        });
    }
}
