package org.golder.sms2webhook;

import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class DigestCache {
    private static DigestCache instance;
    private static final Object monitor = new Object();
    private static String filename = null;
    private Map<String, Object> cache = Collections.synchronizedMap(new HashMap<>());

    private DigestCache() {
        clear();
    }

    public void setFilename(String filename) {
        DigestCache.filename = filename;
    }

    public void add(String cacheKey) {
        cache.put(cacheKey, new Object());
    }

    public boolean exists(String cacheKey) {
        return cache.containsKey(cacheKey);
    }

    public void clear() {
        cache = Collections.synchronizedMap(new HashMap<>());
    }

    public static DigestCache getInstance() {
        if (instance == null) {
            synchronized (monitor) {
                if (instance == null) {
                    instance = new DigestCache();
                }
            }
        }
        return instance;
    }

    public void load() throws IOException {
        if(filename == null) {
            Log.w("digestcache", "No cache filename provided");
            return;
        }

        clear();

        FileInputStream in = new FileInputStream(filename);
        Scanner s = new Scanner(in);
        while(s.hasNextLine()) {
            add(s.nextLine());
        }
        in.close();
    }

    public void save() throws IOException {
        if(filename == null) {
            Log.w("digestcache", "No cache filename provided");
            return;
        }
        FileOutputStream out = new FileOutputStream(filename);
        for (Map.Entry<String,Object> entry : cache.entrySet()) {
            byte[] line = (entry.getKey() + "\n").getBytes(StandardCharsets.UTF_8);
            out.write(line);
        }
        out.close();
    }

    public void setWatermark(int value) {
    }
}
