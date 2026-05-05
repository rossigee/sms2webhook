package org.golder.sms2webhook;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {CacheEntry.class}, version = 2)
public abstract class CacheDatabase extends RoomDatabase {
    public abstract CacheDao cacheDao();
    
    private static volatile CacheDatabase INSTANCE;
    
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Create new table with key as primary key
            database.execSQL("CREATE TABLE IF NOT EXISTS `CacheEntry_new` " +
                    "(`key` TEXT NOT NULL, `value` TEXT, PRIMARY KEY(`key`))");
            
            // Copy data from old table, keeping only the latest value for each key
            database.execSQL("INSERT INTO `CacheEntry_new` (`key`, `value`) " +
                    "SELECT `key`, `value` FROM `CacheEntry` " +
                    "WHERE `id` IN (SELECT MAX(`id`) FROM `CacheEntry` GROUP BY `key`)");
            
            // Drop old table
            database.execSQL("DROP TABLE `CacheEntry`");
            
            // Rename new table
            database.execSQL("ALTER TABLE `CacheEntry_new` RENAME TO `CacheEntry`");
        }
    };
    
    public static CacheDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (CacheDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        CacheDatabase.class,
                        "cache-database"
                    )
                    .addMigrations(MIGRATION_1_2)
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}