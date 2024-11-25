package org.golder.sms2webhook;

import android.content.Context;

import androidx.room.Room;

public class DigestCache {
    private static volatile CacheDatabase INSTANCE;

    private static void checkInstance(Context context) {
        synchronized (CacheDatabase.class) {
            if (INSTANCE == null) {
                INSTANCE = Room.databaseBuilder(context.getApplicationContext(), CacheDatabase.class, "cache_database")
                        .build();
            }
        }
    }

    public static void set(Context context, String key, int value) {
        checkInstance(context);
        INSTANCE.cacheDao().insert(new CacheEntry(key, String.valueOf(value)));
    }

    public static int get(Context context, String key) {
        checkInstance(context);
        if(!INSTANCE.cacheDao().exists(key)) {
            return -1;
        }
        return Integer.parseInt(INSTANCE.cacheDao().get(key));
    }

    public static int getSentCount(Context context) {
        checkInstance(context);
        return INSTANCE.cacheDao().getSent();
    }

    public static int getNotSentCount(Context context) {
        checkInstance(context);
        return INSTANCE.cacheDao().getNotSent();
    }

    public static void clear(Context context) {
        checkInstance(context);

        Runnable r = new Runnable() {
            public void run() {
                INSTANCE.cacheDao().clear();
            }
        };
        new Thread(r).start();
    }
}