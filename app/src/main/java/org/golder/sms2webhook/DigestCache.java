package org.golder.sms2webhook;

import android.util.Log;

import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Singleton class to handle cache operations.
 */
public class DigestCache {

    // Singleton instance
    private static DigestCache instance;

    // Synchronization monitor
    private static final Object monitor = new Object();

    // Cache filename
    private static String filename;

    // Cache store
    private final ConcurrentHashMap<String, Object> cache;

    // Private constructor to prevent instantiation
    private DigestCache() {
        this.cache = new ConcurrentHashMap<>();
        clear();
    }

    /**
     * Sets the filename for cache operations.
     *
     * @param filename The filename for cache operations.
     */
    public void setFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            throw new IllegalArgumentException("Filename cannot be null or empty");
        }
        DigestCache.filename = filename;
    }

    /**
     * Adds a cache key to the cache.
     *
     * @param cacheKey The cache key to be added.
     */
    public void add(String cacheKey) {
        if (cacheKey == null || cacheKey.isEmpty()) {
            throw new IllegalArgumentException("Cache key cannot be null or empty");
        }
        cache.put(cacheKey, new Object());
    }

    /**
     * Checks if a cache key exists in the cache.
     *
     * @param cacheKey The cache key to be checked.
     * @return True if the cache key exists, false otherwise.
     */
    public boolean exists(String cacheKey) {
        if (cacheKey == null || cacheKey.isEmpty()) {
            throw new IllegalArgumentException("Cache key cannot be null or empty");
        }
        return cache.containsKey(cacheKey);
    }

    /**
     * Clears the cache.
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Returns the instance of the DigestCache class.
     *
     * @return The instance of the DigestCache class.
     */
    public static DigestCache getInstance() {
            synchronized (monitor) {
                if (instance == null) {
                    instance = new DigestCache();
                }
        return instance;
    }
    }

    /**
     * Loads the cache from the specified file.
     *
     * @throws IllegalStateException If the filename is not set before loading the cache.
     * @throws IOException          If an I/O error occurs while loading the cache.
     */
    public void load() throws IOException {
        if (filename == null) {
            Log.w("digestcache", "Failed to load digest cache: filename not provided");
            throw new IllegalStateException("Filename not set before loading or saving cache");
        }

        clear();

        Path filePath = Paths.get(filename);
        try (Stream<String> lines = Files.lines(filePath)) {
            lines.forEach(this::add);
        } catch (java.nio.file.NoSuchFileException e) {
            Log.w("digestcache", "Cache file not found: " + filename);
        } catch (java.nio.file.AccessDeniedException e) {
            Log.w("digestcache", "Access denied to cache file: " + filename);
        }
    }

    /**
     * Saves the cache to the specified file.
     *
     * @throws IllegalStateException If the filename is not set before saving the cache.
     * @throws IOException          If an I/O error occurs while saving the cache.
     */
    public void save() throws IOException {
        if (filename == null) {
            Log.w("digestcache", "No cache filename provided");
            throw new IllegalStateException("Filename not set before loading or saving cache");
        }

        // Create the file if it does not exist
        Path filePath = Paths.get(filename);
        try {
            File file = filePath.toFile();
            if (!file.exists()) {
                file.createNewFile();
    }
        } catch (java.io.IOException e) {
            Log.e("digestcache", "Failed to create cache file: " + filename, e);
            throw new IOException(e);
        }

        Files.write(filePath,
                cache.keySet().stream()
                        .map(key -> key + "\n")
                        .collect(Collectors.toList()),
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * Sets the watermark value. This method is currently not implemented.
     *
     * @param value The watermark value.
     */
    public void setWatermark(int value) {
        // Not implemented
    }
}
