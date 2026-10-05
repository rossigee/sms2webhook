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

    private void assertTerminal(int status) {
        assertTrue("status " + status + " should not be uploaded again",
                SmsStoreWorkerStatusHandling.isTerminal(status));
    }

    private void assertNotTerminal(int status) {
        assertFalse("status " + status + " should still be attempted",
                SmsStoreWorkerStatusHandling.isTerminal(status));
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
        // These describe the payload, so the same message gets the same answer
        // forever and re-sending it only burns battery and bandwidth.
        assertPermanent(HttpURLConnection.HTTP_BAD_REQUEST);      // 400
        assertPermanent(HttpURLConnection.HTTP_FORBIDDEN);        // 403
        assertPermanent(413);
        assertPermanent(422);
    }

    @Test
    public void configurationFailures_areRetryable() {
        // Not payload rejections. The reference server answers 401 for a missing,
        // malformed or unknown API key, and Odoo answers 404 when the URL misses
        // its /sms/upload route. Both are corrected by the user in Settings, after
        // which the very same message uploads cleanly, so treating either as final
        // would silently lose a backlog the first time a key was mistyped.
        assertTransient(HttpURLConnection.HTTP_UNAUTHORIZED);    // 401
        assertTransient(HttpURLConnection.HTTP_NOT_FOUND);        // 404
        assertTrue(SmsStoreWorkerStatusHandling.abortsSync(401));
        assertTrue(SmsStoreWorkerStatusHandling.abortsSync(404));
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

    @Test
    public void anySuccess_isSuccess() {
        // The whole 2xx range counts as delivered. Comparing against 200 exactly
        // reported a 201 or 204 as a failed upload.
        assertTrue(SmsStoreWorkerStatusHandling.isSuccess(200));
        assertTrue(SmsStoreWorkerStatusHandling.isSuccess(HttpURLConnection.HTTP_CREATED));
        assertTrue(SmsStoreWorkerStatusHandling.isSuccess(HttpURLConnection.HTTP_ACCEPTED));
        assertTrue(SmsStoreWorkerStatusHandling.isSuccess(HttpURLConnection.HTTP_NO_CONTENT));
        assertTrue(SmsStoreWorkerStatusHandling.isSuccess(299));
    }

    @Test
    public void nonSuccess_isNotSuccess() {
        assertFalse(SmsStoreWorkerStatusHandling.isSuccess(199));
        assertFalse(SmsStoreWorkerStatusHandling.isSuccess(300));
        assertFalse(SmsStoreWorkerStatusHandling.isSuccess(HttpURLConnection.HTTP_BAD_REQUEST));
        assertFalse(SmsStoreWorkerStatusHandling.isSuccess(503));
        assertFalse(SmsStoreWorkerStatusHandling.isSuccess(SmsStoreWorkerStatusHandling.TRANSPORT_FAILURE));
    }

    @Test
    public void deliveredMessages_areTerminal() {
        assertTerminal(200);
        assertTerminal(201);
        assertTerminal(204);
    }

    @Test
    public void permanentlyRefusedMessages_areTerminal() {
        // The regression. A 4xx describing the payload used to be recorded but
        // never consulted, because the sync skipped only on an exact 200, so these
        // were re-sent on every single sync for the life of the install.
        assertTerminal(HttpURLConnection.HTTP_BAD_REQUEST);
        assertTerminal(HttpURLConnection.HTTP_FORBIDDEN);
        assertTerminal(413);
        assertTerminal(422);
    }

    @Test
    public void configurationFailures_areNotTerminal() {
        // The inverse: 401 and 404 say the app is misconfigured, not that the
        // message is undeliverable. Caching them as done loses the message for good
        // once the user fixes the key or the URL.
        assertNotTerminal(HttpURLConnection.HTTP_UNAUTHORIZED);
        assertNotTerminal(HttpURLConnection.HTTP_NOT_FOUND);
    }

    @Test
    public void transientOutcomes_areNotTerminal() {
        // Nothing was dealt with, so the message must be attempted again.
        assertNotTerminal(401);
        assertNotTerminal(404);
        assertNotTerminal(408);
        assertNotTerminal(425);
        assertNotTerminal(429);
        assertNotTerminal(500);
        assertNotTerminal(502);
        assertNotTerminal(503);
        assertNotTerminal(504);
    }

    @Test
    public void aMissingCacheEntry_isNotTerminal() {
        // Both of these mean "no outcome recorded", never "already finished", so
        // neither may suppress an upload.
        assertNotTerminal(SmsStoreWorkerStatusHandling.TRANSPORT_FAILURE);
        assertNotTerminal(0);
    }

    @Test
    public void anUnansweredRedirect_isTerminal() {
        // A redirect is a misconfigured endpoint, so it will keep happening. Left
        // non-terminal it was re-requested on every sync.
        assertTerminal(301);
        assertTerminal(302);
        assertTerminal(307);
    }
    @Test
    public void everyStatusClassIsEitherTerminalOrRetryable() {
        // Nothing may fall between the two: an outcome that is neither would be
        // recorded and then re-attempted forever.
        for (int status = 100; status <= 599; status++) {
            boolean terminal = SmsStoreWorkerStatusHandling.isTerminal(status);
            boolean retryable = SmsStoreWorkerStatusHandling.isTransient(status);
            assertTrue("status " + status + " is neither terminal nor transient",
                    terminal ^ retryable);
        }
    }
}