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
     * Server faults, transport failures, rate limiting and client-configuration
     * failures are transient. A 4xx that describes the message itself is not.
 *
     * <p>429 and 425 sit in the 4xx range but must be retried: they say nothing
     * about this message, only that the server is throttling or not ready. Treating
     * them as permanent would silently discard messages whenever the server
 * * applied backpressure. 408 is included for the same reason.
     *
     * <p>401 and 404 are here for the same underlying reason, and for a stronger
     * one: they describe how the app is configured, not what the message contains.
     * The reference server answers 401 for a missing, malformed or unrecognised API
     * key, and Odoo answers 404 when the webhook URL does not resolve to its
     * {@code /sms/upload} route. Both are corrected by the user in Settings, and a
     * message refused for either reason is perfectly deliverable afterwards. Caching
     * them as final outcomes is how a mistyped API key silently destroyed a backlog:
     * every message was marked permanently refused, so fixing the key uploaded none
     * of them. Retrying holds the message instead, and aborting on the first one
     * avoids sending a request per message in the meantime.
     */
    static boolean isTransient(int statusCode) {
        if (statusCode == TRANSPORT_FAILURE) {
            return true;
        }
        if (statusCode == 401 || statusCode == 404) {
            return true;
        }
        if (statusCode == 408 || statusCode == 425 || statusCode == 429) {
            return true;
        }
        return statusCode >= 500;
    }

    /**
     * True when the server took the message.
     *
     * <p>Any 2xx, not just 200. A webhook that answers 201 or 204 has accepted
     * the message, and treating those as failures made the sync report a
     * successful delivery as an error.
     */
    static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * True when the outcome is final, so the message must not be attempted again.
     *
     * <p>Either the server took it (any {@link #isSuccess(int)}) or it permanently
     * refused it. Both are recorded in the cache, and this is what the sync checks
     * before uploading, so anything terminal here is never re-sent.
     *
     * <p>Non-positive statuses are never terminal. {@link #TRANSPORT_FAILURE} is
     * the same value the cache lookup returns for a key that is absent, and
     * neither means a message was dealt with.
     */
    static boolean isTerminal(int statusCode) {
        if (statusCode <= 0) {
            return false;
        }
        return !isTransient(statusCode);
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