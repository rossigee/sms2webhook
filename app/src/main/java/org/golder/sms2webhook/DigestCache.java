package org.golder.sms2webhook;

import android.content.Context;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import androidx.room.Room;

public class DigestCache {
    private static volatile CacheDatabase INSTANCE;

    public static void set(Context context, String key, String value) {
        synchronized (CacheDatabase.class) {
            if (INSTANCE == null) {
                INSTANCE = Room.databaseBuilder(context.getApplicationContext(), CacheDatabase.class, "cache_database")
                            .build();
            }
        }

        INSTANCE.cacheDao().insert(new CacheEntry(key, value));
    }

    public static String get(Context context, String key) {
        synchronized (CacheDatabase.class) {
            if (INSTANCE == null) {
                INSTANCE = Room.databaseBuilder(context.getApplicationContext(), CacheDatabase.class, "cache_database")
                        .build();
            }
        }

        return INSTANCE.cacheDao().get(key);
    }

    public static void clear(Context context) {
        synchronized (CacheDatabase.class) {
            if (INSTANCE == null) {
                INSTANCE = Room.databaseBuilder(context.getApplicationContext(), CacheDatabase.class, "cache_database")
                        .build();
            }
        }

       FutureTask<String> future = new FutureTask<>(() -> {
            INSTANCE.cacheDao().clear();
            return null;
        });
        Executor executor = Executors.newSingleThreadExecutor();
        executor.execute(future);
    }
}