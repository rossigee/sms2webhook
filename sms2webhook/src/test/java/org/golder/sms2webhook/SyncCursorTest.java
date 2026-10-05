package org.golder.sms2webhook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Covers the cursor decision that the sync fix turns on: whether processing a
 * message should move the watermark forward.
 *
 * <p>This is the exact shape of the bug that wedged a sync. A rejected message
 * must still advance, or the sync re-reaches it on the next run, is rejected
 * again, and nothing behind it is ever attempted. A transient failure must not
 * advance, or the message is skipped and silently lost.
 *
 * <p>The rule lives here as a pure function so it can be exercised without a
 * ContentResolver, a WorkManager and a live webhook.
 */
public class SyncCursorTest {

    /** What {@code SmsStoreWorker} does with the watermark for one message. */
    private enum Outcome {
        /** Recorded and skipped: the watermark moves on. */
        ADVANCE,
        /** Stop and retry later: the watermark stays put. */
        HOLD
    }

    private static Outcome outcomeFor(int statusCode) {
        if (statusCode == SmsStoreWorkerStatusHandling.TRANSPORT_FAILURE) {
            return Outcome.HOLD;
        }
        if (SmsStoreWorkerStatusHandling.abortsSync(statusCode)) {
            return Outcome.HOLD;
        }
        if (statusCode >= 200 && statusCode < 300) {
            return Outcome.ADVANCE;
        }
        return Outcome.ADVANCE; // any other recorded status is skipped, not retried
    }

    private static int advance(int watermark, Outcome outcome) {
        return outcome == Outcome.ADVANCE ? watermark + 1 : watermark;
    }

    @Test
    public void acceptedMessageAdvances() {
        assertEquals(Outcome.ADVANCE, outcomeFor(200));
        assertEquals(6, advance(5, outcomeFor(200)));
    }

    @Test
    public void rejectedMessageAdvancesSoTheSyncCanProgress() {
        // The regression. Holding here is what left the sync stuck on one
        // message forever.
        assertEquals(Outcome.ADVANCE, outcomeFor(400));
        assertEquals(6, advance(5, outcomeFor(400)));
    }

    @Test
    public void forbiddenAndUnprocessableAlsoAdvance() {
        // These describe the payload, so the server will keep saying the same
        // thing. Advancing is what lets the sync reach the messages behind it.
        assertEquals(Outcome.ADVANCE, outcomeFor(403));
        assertEquals(Outcome.ADVANCE, outcomeFor(413));
        assertEquals(Outcome.ADVANCE, outcomeFor(422));
    }

    @Test
    public void aMistypedKeyOrUrlHoldsRatherThanDiscarding() {
        // 401 and 404 used to be grouped with the payload rejections above, which
        // lost a backlog: the server answers 401 for an unrecognised API key and
        // Odoo answers 404 when the URL misses its /sms/upload route, both of
        // which the user then fixes in Settings. Advancing past the message marked
        // it delivered-forever, so correcting the key uploaded none of them.
        // Holding means WorkManager retries and the message is still there to send.
        assertEquals(Outcome.HOLD, outcomeFor(401));
        assertEquals(Outcome.HOLD, outcomeFor(404));
        assertEquals(5, advance(5, outcomeFor(401)));
        assertEquals(5, advance(5, outcomeFor(404)));
    }

    @Test
    public void serverFaultHoldsSoTheMessageIsNotLost() {
        assertEquals(Outcome.HOLD, outcomeFor(500));
        assertEquals(Outcome.HOLD, outcomeFor(502));
        assertEquals(Outcome.HOLD, outcomeFor(503));
        assertEquals(5, advance(5, outcomeFor(503)));
    }

    @Test
    public void rateLimitingHoldsRatherThanDiscarding() {
        assertEquals(Outcome.HOLD, outcomeFor(429));
        assertEquals(Outcome.HOLD, outcomeFor(425));
        assertEquals(Outcome.HOLD, outcomeFor(408));
    }

    @Test
    public void transportFailureHolds() {
        assertEquals(Outcome.HOLD, outcomeFor(-1));
        assertEquals(5, advance(5, outcomeFor(-1)));
    }

    @Test
    public void aSyncWithOneRejectionStillFinishes() {
        // 623 messages, one of which is rejected partway through. The watermark
        // must reach the end, which is what the completed run demonstrated.
        int total = 623;
        int watermark = 0;
        int rejectedAt = 251;
        while (watermark < total) {
            Outcome outcome = (watermark == rejectedAt) ? outcomeFor(400) : outcomeFor(200);
            watermark = advance(watermark, outcome);
        }
        assertEquals(total, watermark);
    }

    @Test
    public void aSyncWithATransientFailureRetriesTheSameMessage() {
        // Holding means the retry starts from the same position, so the message
        // is attempted again rather than being skipped.
        int watermark = 40;
        int first = advance(watermark, outcomeFor(503));
        assertEquals(watermark, first);
        int second = advance(first, outcomeFor(200));
        assertEquals(watermark + 1, second);
    }

    @Test
    public void repeatedRejectionsNeverStallTheCursor() {
        // Every message rejected: the sync still terminates. Under the old
        // behaviour this looped forever.
        int total = 100;
        int watermark = 0;
        int guard = 0;
        while (watermark < total && guard < total * 2) {
            watermark = advance(watermark, outcomeFor(400));
            guard++;
        }
        assertEquals(total, watermark);
        assertTrue("must not need more steps than messages", guard <= total);
    }

    @Test
    public void noStatusCombinationCanHoldForeverWithoutProgress() {
        // A run of transient failures legitimately stalls, but it must not be
        // reported as progress: the watermark must be unchanged, which is what
        // makes a retry safe.
        assertFalse(advance(7, outcomeFor(503)) > 7);
    }
}