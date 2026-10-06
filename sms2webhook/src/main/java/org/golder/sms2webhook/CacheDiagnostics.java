package org.golder.sms2webhook;

import java.util.ArrayList;
import java.util.List;

/**
 * Consistency checks over the digest cache, split out so they can be exercised
 * without a ContentResolver or a Room database.
 */
final class CacheDiagnostics {

    private CacheDiagnostics() {
    }

    /** One thing worth telling the user about the cache. */
    static final class Finding {
        final String message;
        final MainViewModel.LogEntry.Type type;

        Finding(String message, MainViewModel.LogEntry.Type type) {
            this.message = message;
            this.type = type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Finding)) {
                return false;
            }
            Finding other = (Finding) o;
            return type == other.type && message.equals(other.message);
        }

        @Override
        public int hashCode() {
            return 31 * message.hashCode() + type.hashCode();
        }
    }

    /**
     * Evaluates the cache against the provider's message count.
     *
     * @param storeCount       messages the provider is holding
     * @param uniqueCount      distinct hashes recorded
     * @param totalCacheEntries rows in the cache, which can exceed the distinct count
     * @param sentCount        messages the server accepted
     * @param refusedCount     messages the server permanently refused
     */
    static List<Finding> evaluate(int storeCount, int uniqueCount, int totalCacheEntries,
                                  int sentCount, int refusedCount) {
        List<Finding> findings = new ArrayList<>();

        if (totalCacheEntries > uniqueCount) {
            findings.add(new Finding(
                    "⚠️ Found " + (totalCacheEntries - uniqueCount) + " duplicate cache entries",
                    MainViewModel.LogEntry.Type.WARNING));
        }

        if (storeCount > uniqueCount) {
            findings.add(new Finding(
                    "ℹ️ " + (storeCount - uniqueCount) + " messages have no cache entry",
                    MainViewModel.LogEntry.Type.INFO));
        }

        if (sentCount + refusedCount != uniqueCount && totalCacheEntries == uniqueCount) {
            findings.add(new Finding(
                    "⚠️ Cache inconsistency detected: " + uniqueCount + " unique messages, but "
                            + sentCount + " sent + " + refusedCount + " refused",
                    MainViewModel.LogEntry.Type.WARNING));
        }

        return findings;
    }

    /**
     * The findings that are new since {@code previous}.
     *
     * <p>Diagnostics run on every statistics load, which is every activity launch,
     * every Clear Cache and every sync exit. Logging each finding every time filled
     * the activity log with the same line repeated — four copies of
     * "624 messages have no cache entry" were visible at rest on a real device.
     *
     * <p>Compared by value, so a finding that is still true is suppressed and a
     * count that changes reports the new number. Passing {@code null} means nothing
     * has been reported yet, and returns everything.
     */
    static List<Finding> sinceReported(List<Finding> previous, List<Finding> current) {
        List<Finding> fresh = new ArrayList<>();
        for (Finding finding : current) {
            if (previous == null || !previous.contains(finding)) {
                fresh.add(finding);
            }
        }
        return fresh;
    }
}