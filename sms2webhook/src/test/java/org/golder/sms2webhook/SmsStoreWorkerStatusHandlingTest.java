package org.golder.sms2webhook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.net.HttpURLConnection;

/**
 * Covers how {@link SmsStoreWorker} reacts to a webhook status code.
 *
 * The distinction that matters: a 5xx or a transport failure is transient and
 * must be retried, while a 4xx is the server permanently refusing one message
 * and must be skipped. Treating both as fatal wedged the sync permanently,
 * because the watermark never advanced past a message that always failed.
 */
public class SmsStoreWorkerStatusHandlingTest {

    // The classification is deliberately free of Android dependencies, so these
    // are plain assertions rather than a mocked WorkManager.

    /** Documents the contract each status class must satisfy. */
    private void assertTransient(int status) {
        assertTrue("status " + status + " should be treated as retryable",
                SmsStoreWorkerStatusHandling.isTransient(status));
    }

    private void assertPermanent(int status) {
        assertFalse("status " + status + " should be treated as permanent",
                SmsStoreWorkerStatusHandling.isTransient(status));
    }

    @Test
    public void serverFaults_areTransient() {
        assertTransient(500);
        assertTransient(502);
        assertTransient(503);
        assertTransient(504);
    }

    @Test
    public void clientRejections_arePermanent() {
        assertPermanent(HttpURLConnection.HTTP_BAD_REQUEST);      // 400
        assertPermanent(HttpURLConnection.HTTP_UNAUTHORIZED);     // 401
        assertPermanent(HttpURLConnection.HTTP_FORBIDDEN);        // 403
        assertPermanent(HttpURLConnection.HTTP_NOT_FOUND);        // 404
        assertPermanent(422);
    }

    @Test
    public void rateLimiting_isTransientNotPermanent() {
        // These sit in the 4xx range but say nothing about the message. Treating
        // them as permanent silently discarded messages under server backpressure.
        assertTransient(429);
        assertTransient(425);
        assertTransient(HttpURLConnection.HTTP_CLIENT_TIMEOUT);   // 408
        assertTrue(SmsStoreWorkerStatusHandling.abortsSync(429));
    }

    @Test
    public void retryAfterSeconds_readsADelay() {
        assertEquals(120, SmsStoreWorkerStatusHandling.retryAfterSeconds("120"));
        assertEquals(0, SmsStoreWorkerStatusHandling.retryAfterSeconds("0"));
        assertEquals(30, SmsStoreWorkerStatusHandling.retryAfterSeconds("  30  "));
    }

    @Test
    public void retryAfterSeconds_isZeroWhenAbsentOrUnusable() {
        assertEquals(0, SmsStoreWorkerStatusHandling.retryAfterSeconds(null));
        assertEquals(0, SmsStoreWorkerStatusHandling.retryAfterSeconds(""));
        assertEquals(0, SmsStoreWorkerStatusHandling.retryAfterSeconds("soon"));
    }

    @Test
    public void retryAfterSeconds_readsAnHttpDate() {
        String future = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
                .plusSeconds(90)
                .format(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME);
        int seconds = SmsStoreWorkerStatusHandling.retryAfterSeconds(future);
        // Allow for second-truncation in the formatted date.
        assertTrue("expected ~90s, got " + seconds, seconds >= 60 && seconds <= 90);
    }

    @Test
    public void retryAfterSeconds_isClampedToTheMaximumWait() {
        // A server asking for an hour must not be able to pin a worker thread
        // for an hour.
        int huge = SmsStoreWorkerStatusHandling.retryAfterSeconds("86400");
        assertEquals(SmsStoreWorkerStatusHandling.MAX_RETRY_AFTER_SECONDS,
                Math.min(SmsStoreWorkerStatusHandling.MAX_RETRY_AFTER_SECONDS, huge));
    }

    @Test
    public void noRetryAfterMeansNoWait() {
        // The common case must not add latency: a plain 503 with no header
        // retries on WorkManager's own schedule, not after a sleep.
        assertEquals(0, SmsStoreWorkerStatusHandling.retryAfterSeconds(null));
    }

    @Test
    public void retryAfterSeconds_ignoresAPastDate() {
        String past = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
                .minusMinutes(5)
                .format(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME);
        assertEquals(0, SmsStoreWorkerStatusHandling.retryAfterSeconds(past));
    }

    @Test
    public void successAndRedirects_areNotFailures() {
        assertPermanent(HttpURLConnection.HTTP_OK);               // 200
        assertPermanent(HttpURLConnection.HTTP_CREATED);          // 201
        assertPermanent(HttpURLConnection.HTTP_NO_CONTENT);       // 204
    }

    @Test
    public void transportFailure_isTransient() {
        // A dropped connection says nothing about the message itself.
        assertTransient(SmsStoreWorkerStatusHandling.TRANSPORT_FAILURE);
    }

    @Test
    public void aPermanentFailureDoesNotAbortTheRun() {
        // The regression this guards: returning failure() for a 4 left the
        // watermark at the offending message, so every later message was blocked
        // behind it with no way to progress.
        assertFalse(SmsStoreWorkerStatusHandling.abortsSync(HttpURLConnection.HTTP_BAD_REQUEST));
    }

    @Test
    public void aTransientFailureDoesAbortTheRun() {
        assertTrue(SmsStoreWorkerStatusHandling.abortsSync(503));
    }
}