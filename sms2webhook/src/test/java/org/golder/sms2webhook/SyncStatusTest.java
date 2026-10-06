package org.golder.sms2webhook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * Covers the sync status shown on the main screen.
 *
 * <p>This replaces three counters that read Uploaded 0 / Failed 0 on a device
 * holding 624 messages with an empty cache — indistinguishable from a fully synced
 * phone. The mapping exists to make "is anything outstanding" answerable, so the
 * cases that used to mislead are the ones pinned here.
 */
public class SyncStatusTest {

    @Test
    public void aFreshInstallIsUnsyncedNotEmpty() {
        // The regression. 624 messages, nothing uploaded: the old counters showed
        // Uploaded 0 and Failed 0, which reads as "nothing to do".
        SyncStatus status = SyncStatus.of(624, 0, 0, false, 0);

        assertEquals(SyncStatus.State.UNSYNCED, status.state);
        assertEquals(624, status.count);
    }

    @Test
    public void everythingDeliveredIsSynced() {
        SyncStatus status = SyncStatus.of(624, 624, 0, false, 100);
        assertEquals(SyncStatus.State.SYNCED, status.state);
    }

    @Test
    public void partlyDeliveredCountsWhatIsOutstanding() {
        SyncStatus status = SyncStatus.of(624, 600, 0, false, 0);
        assertEquals(SyncStatus.State.UNSYNCED, status.state);
        assertEquals(24, status.count);
    }

    @Test
    public void refusedOutranksEverything() {
        // Refused messages are never retried, so they need acting on rather than
        // syncing, and they must not be reported as merely outstanding.
        assertEquals(SyncStatus.State.REFUSED,
                SyncStatus.of(624, 600, 3, false, 0).state);
        assertEquals(SyncStatus.State.REFUSED,
                SyncStatus.of(624, 600, 3, true, 50).state);
    }

    @Test
    public void refusedWinsEvenWithNothingElseOutstanding() {
        SyncStatus status = SyncStatus.of(624, 624, 1, false, 0);
        assertEquals(SyncStatus.State.REFUSED, status.state);
        assertEquals(1, status.count);
    }

    @Test
    public void syncingIsItsOwnState() {
        SyncStatus status = SyncStatus.of(624, 0, 0, true, 42);
        assertEquals(SyncStatus.State.SYNCING, status.state);
        assertEquals(42, status.percent);
    }

    @Test
    public void theRemainderIsNeverNegative() {
        // The cache outlives deleted messages, so it can record more than the
        // provider holds. That must not surface as a negative count.
        SyncStatus status = SyncStatus.of(10, 25, 0, false, 0);
        assertEquals(SyncStatus.State.SYNCED, status.state);
        assertTrue("no negative counts", status.count >= 0);
    }

    @Test
    public void anEmptyInboxIsSynced() {
        assertEquals(SyncStatus.State.SYNCED, SyncStatus.of(0, 0, 0, false, 0).state);
    }

    @Test
    public void headlineCountsAreSingularWhereTheyShouldBe() {
        assertEquals("1 not uploaded yet", SyncStatus.of(1, 0, 0, false, 0).headline());
        assertEquals("2 not uploaded yet", SyncStatus.of(2, 0, 0, false, 0).headline());
        assertEquals("1 uploaded", SyncStatus.of(1, 1, 0, false, 0).headline());
        assertEquals("3 refused", SyncStatus.of(9, 6, 3, false, 0).headline());
    }

    @Test
    public void onlyStatesThatNeedACallToActionCarryDetail() {
        assertEquals("Tap Sync to send them.", SyncStatus.of(10, 0, 0, false, 0).detail());
        assertFalse("an all-clear needs no detail", SyncStatus.of(10, 10, 0, false, 0).detail() != null);
        assertTrue("refused must explain that nothing will retry it",
                SyncStatus.of(10, 10, 1, false, 0).detail().contains("not retried"));
    }

    @Test
    public void anUnreadableInboxIsNotAnEmptyOne() {
        // The defect a device run found: with the SMS permission refused the count
        // came back zero, so a phone holding 438 messages showed a green
        // "0 uploaded" — the exact false-clear this replaced.
        SyncStatus status = SyncStatus.of(0, 0, 0, false, 0, false);

        assertEquals(SyncStatus.State.CANNOT_READ, status.state);
        assertFalse("must not read as all-clear",
                status.state == SyncStatus.State.SYNCED);
        assertTrue(status.detail().contains("permission"));
    }

    @Test
    public void unreadableOutranksRefusedAndSyncing() {
        // Whatever else is true, not being able to see the inbox is the more
        // important thing to say.
        assertEquals(SyncStatus.State.CANNOT_READ,
                SyncStatus.of(0, 0, 3, true, 40, false).state);
    }

    @Test
    public void aReadableInboxStillMapsNormally() {
        assertEquals(SyncStatus.State.SYNCED, SyncStatus.of(0, 0, 0, false, 0, true).state);
        assertEquals(SyncStatus.State.UNSYNCED, SyncStatus.of(10, 0, 0, false, 0, true).state);
    }

    @Test
    public void everyCountCombinationLandsInAState() {
        for (int inbox = 0; inbox <= 5; inbox++) {
            for (int uploaded = 0; uploaded <= 5; uploaded++) {
                for (int refused = 0; refused <= 3; refused++) {
                    SyncStatus status = SyncStatus.of(inbox, uploaded, refused, false, 0);
                    assertTrue("unmapped state for " + inbox + "/" + uploaded + "/" + refused,
                            status.state != null);
                    assertTrue(status.headline() != null && !status.headline().isEmpty());
                }
            }
        }
    }

    @Test
    public void statesAreOrderedByUrgency() {
        // REFUSED must beat SYNCING and UNSYNCED, because it is the only one the
        // user has to act on outside a sync.
        assertTrue(SyncStatus.of(0, 0, 1, false, 0).state == SyncStatus.State.REFUSED);
        assertTrue(SyncStatus.of(0, 0, 0, true, 0).state == SyncStatus.State.SYNCING);
        assertTrue(SyncStatus.of(0, 0, 0, false, 0).state == SyncStatus.State.SYNCED);
    }
}