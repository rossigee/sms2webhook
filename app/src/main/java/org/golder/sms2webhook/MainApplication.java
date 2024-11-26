package org.golder.sms2webhook;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Handler;
import android.provider.Telephony;

import java.util.ArrayList;

import androidx.preference.PreferenceManager;

public class MainApplication extends Application {
    private MainActivity mainActivity;

    int watermark = 0;

    int inboxCount = 0;
    int sentCount = 0;
    int unsentCount = 0;

    private final ArrayList<String> messages = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();

        Context ctx = getApplicationContext();

        Cursor cursor = getContentResolver().query(Telephony.Sms.CONTENT_URI, null, null, null, "_id");
        inboxCount = cursor.getCount();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        watermark = prefs.getInt("watermark", 0);

        new Thread(() -> {
            sentCount = DigestCache.getSentCount(ctx);
            unsentCount = DigestCache.getNotSentCount(ctx);
        }).start();
    }

    public void setMainActivity(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }

    public String[] getMessages() {
        return messages.toArray(new String[0]);
    }

    public void setWatermark(int level) {
        watermark = level;
        updateStats();
    }

    public void updateStats() {
        Context ctx = getApplicationContext();
        Handler handler = new Handler(ctx.getMainLooper());
        handler.post(() -> {
            if(mainActivity != null) {
                mainActivity.updateStats(ctx);
            }
        });
    }

    public void restoreMessages() {
        Context ctx = getApplicationContext();
        Handler handler = new Handler(ctx.getMainLooper());
        handler.post(() -> {
            if(mainActivity != null) {
                mainActivity.restoreMessages();
            }
        });
    }

    public void addMessage(String line) {
        messages.add(line);
        Context ctx = getApplicationContext();
        Handler handler = new Handler(ctx.getMainLooper());
        handler.post(() -> {
            if(mainActivity != null) {
                mainActivity.addMessage(line);
            }
        });
    }
}
