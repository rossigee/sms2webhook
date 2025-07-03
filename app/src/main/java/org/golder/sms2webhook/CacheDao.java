package org.golder.sms2webhook;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface CacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CacheEntry entry);

    @Query("SELECT COUNT(*) FROM cacheentry WHERE key = :key")
    boolean exists(String key);

    @Query("SELECT value FROM cacheentry WHERE key = :key")
    String get(String key);

    @Query("SELECT COUNT(*) FROM cacheentry WHERE value = 200")
    int getSent();

    @Query("SELECT COUNT(*) FROM cacheentry WHERE value != 200")
    int getNotSent();

    @Query("DELETE FROM cacheentry")
    void clear();
    
    @Query("SELECT COUNT(DISTINCT key) FROM cacheentry")
    int getUniqueCount();
    
    @Query("SELECT COUNT(*) FROM cacheentry")
    int getTotalCount();
}