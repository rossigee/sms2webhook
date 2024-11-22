package org.golder.sms2webhook;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {CacheEntry.class}, version = 1)
public abstract class CacheDatabase extends RoomDatabase {
    public abstract CacheDao cacheDao();
}