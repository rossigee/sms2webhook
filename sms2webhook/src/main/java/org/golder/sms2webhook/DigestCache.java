package org.golder.sms2webhook;

import android.content.Context;
import android.util.Log;

public class DigestCache {
    private static final String TAG = DigestCache.class.getSimpleName();

    private static CacheDatabase getDatabase(Context context) {
        return CacheDatabase.getInstance(context);
    }

    public static void set(Context context, String key, int value) {
        CacheDatabase db = getDatabase(context);
        db.cacheDao().insert(new CacheEntry(key, String.valueOf(value)));
    }

    /**
     * Looks up the recorded outcome for a message.
     *
     * @return the status of its last terminal outcome, or
     *         {@link SmsStoreWorkerStatusHandling#TRANSPORT_FAILURE} when the message
     *         has never been dealt with, which is also what an unreadable entry
     *         reports
     */
    public static int get(Context context, String key) {
        CacheDatabase db = getDatabase(context);
        String value = db.cacheDao().get(key);
        if (value == null) {
            return SmsStoreWorkerStatusHandling.TRANSPORT_FAILURE;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            // A single corrupt row must not abandon the whole sync. Reporting it as
            // unrecorded re-attempts the message, which is the safe direction: a
            // duplicate upload is recoverable, a dropped one is not.
            Log.w(TAG, "Discarding unreadable cache entry: " + e.getMessage());
            return SmsStoreWorkerStatusHandling.TRANSPORT_FAILURE;
        }
    }
}