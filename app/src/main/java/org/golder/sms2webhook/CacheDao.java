package org.golder.sms2webhook;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

@Dao
public interface CacheDao {

    @Insert
    void insert(CacheEntry entry);

    @Query("SELECT value FROM cacheentry WHERE key = :key")
    String get(String key);

    @Query("DELETE FROM cacheentry")
    void clear();
}