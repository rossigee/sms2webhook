package org.golder.sms2webhook;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class CacheEntry {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "key")
    public String key;

    @ColumnInfo(name = "value")
    public String value;

    public CacheEntry(@NonNull String key, String value) {
        this.key = key;
        this.value = value;
    }
}