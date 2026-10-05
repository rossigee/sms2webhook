package org.golder.sms2webhook;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface CacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CacheEntry entry);

    @Query("SELECT value FROM cacheentry WHERE key = :key")
    String get(String key);

    /**
     * Counts the messages the server accepted.
     *
     * <p>The whole 2xx range, not just 200: a webhook that answers 201 or 204 has
     * taken the message, and counting those as unsent made the dashboard disagree
     * with what the server actually holds. The cast is needed because the column
     * holds the status as text, and comparing it against a bare integer literal
     * only works through SQLite's implicit affinity rules.
     */
    @Query("SELECT COUNT(*) FROM cacheentry WHERE CAST(value AS INTEGER) BETWEEN 200 AND 299")
    int getSent();

    /**
     * Counts the messages the server permanently refused.
     *
     * <p>Only terminal outcomes are recorded, so everything here is a message the
     * sync will not try again.
     */
    @Query("SELECT COUNT(*) FROM cacheentry WHERE CAST(value AS INTEGER) NOT BETWEEN 200 AND 299")
    int getNotSent();

    @Query("DELETE FROM cacheentry")
    void clear();
    
    @Query("SELECT COUNT(DISTINCT key) FROM cacheentry")
    int getUniqueCount();
    
    @Query("SELECT COUNT(*) FROM cacheentry")
    int getTotalCount();
}