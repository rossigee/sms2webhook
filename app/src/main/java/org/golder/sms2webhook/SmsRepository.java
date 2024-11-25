package org.golder.sms2webhook;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.provider.Telephony;
import android.util.Log;

import java.util.ArrayList;

import androidx.preference.PreferenceManager;

class SmsRepository {
    private static final String TAG = SmsRepository.class.getSimpleName();

    static class Status {
        int inboxCount = 0;
        int watermark = 0;
        int processedCount = 0;
        int retryCount = 0;
        String[] messages = new String[0];

        public Status() {}
    }

    public static Status fetchStatus(Context ctx) {
        Log.d(TAG, "Fetch status callback called");

        Status status = new SmsRepository.Status();

        Cursor cursor = ctx.getContentResolver().query(Telephony.Sms.CONTENT_URI, null, null, null, "_id");
        status.inboxCount = cursor.getCount();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        status.watermark = prefs.getInt("watermark", 0);

        status.processedCount = DigestCache.getSentCount(ctx);
        status.retryCount = DigestCache.getNotSentCount(ctx);

        MainApplication mApplication = (MainApplication)ctx.getApplicationContext();
        status.messages = mApplication.getMessages();

        return status;
    }
}