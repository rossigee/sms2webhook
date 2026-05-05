package org.golder.sms2webhook;

import android.content.Context;

import androidx.room.Room;

public class DigestCache {
    private static CacheDatabase getDatabase(Context context) {
        return CacheDatabase.getInstance(context);
    }

    public static void set(Context context, String key, int value) {
        CacheDatabase db = getDatabase(context);
        db.cacheDao().insert(new CacheEntry(key, String.valueOf(value)));
    }

    public static int get(Context context, String key) {
        CacheDatabase db = getDatabase(context);
        if(!db.cacheDao().exists(key)) {
            return -1;
        }
        return Integer.parseInt(db.cacheDao().get(key));
    }

    public static int getSentCount(Context context) {
        CacheDatabase db = getDatabase(context);
        return db.cacheDao().getSent();
    }

    public static int getNotSentCount(Context context) {
        CacheDatabase db = getDatabase(context);
        return db.cacheDao().getNotSent();
    }

    public static void clear(Context context) {
        CacheDatabase db = getDatabase(context);
        Runnable r = new Runnable() {
            public void run() {
                db.cacheDao().clear();
            }
        };
        new Thread(r).start();
    }
}