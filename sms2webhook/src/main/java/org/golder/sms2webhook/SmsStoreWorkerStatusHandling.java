package org.golder.sms2webhook;

import java.net.HttpURLConnection;

/**
 * Classification of webhook responses during a sync.
 *
 * Split out from {@link SmsStoreWorker} so the decision can be tested without a
 * ContentResolver, a WorkManager and a live webhook.
 */
final class SmsStoreWorkerStatusHandling {

    /** Recorded for an upload that failed before a response was received. */
    static final int TRANSPORT_FAILURE = -1;

    private SmsStoreWorkerStatusHandling() {
    }

    /**
     * True when the failure is worth retrying.
     *
     * Server faults, transport failures and rate limiting are transient. A 4xx
     * that describes the message itself is not.
     *
     * <p>429 and 425 sit in the 4xx range but must be retried: they say nothing
     * about this message, only that the server is throttling or not ready. Treating
     * them as permanent would silently discard messages whenever the server
     * applied backpressure. 408 is included for the same reason.
     */
    static boolean isTransient(int statusCode) {
        if (statusCode == TRANSPORT_FAILURE) {
            return true;
        }
        if (statusCode == 408 || statusCode == 425 || statusCode == 429) {
            return true;
        }
        return statusCode >= 500;
    }

    /**
     * Seconds to wait before retrying, from a {@code Retry-After} header.
     *
     * <p>The header is either a delay in seconds or an HTTP date. Returns 0 when
     * absent or unparseable, leaving the caller's backoff in charge.
     */
    static int retryAfterSeconds(String headerValue) {
        if (headerValue == null) {
            return 0;
        }
        String trimmed = headerValue.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        try {
            int seconds = Integer.parseInt(trimmed);
            return Math.max(0, seconds);
        } catch (NumberFormatException ignored) {
            // Not a delay; fall through to the date form.
        }
        try {
            long target = java.time.ZonedDateTime
                    .parse(trimmed, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant()
                    .toEpochMilli();
            long deltaMillis = target - System.currentTimeMillis();
            return deltaMillis <= 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, deltaMillis / 1000L);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    /** Upper bound on a server-requested delay, so a bad header cannot stall a sync for hours. */
    static final int MAX_RETRY_AFTER_SECONDS = 300;

    /**
     * True when the sync should stop and be retried as a whole.
     *
     * Only transient failures qualify. Aborting on a permanent failure wedges
     * the sync, because the offending message is retried identically forever and
     * nothing behind it is ever attempted.
     */
    static boolean abortsSync(int statusCode) {
        return isTransient(statusCode);
    }
}