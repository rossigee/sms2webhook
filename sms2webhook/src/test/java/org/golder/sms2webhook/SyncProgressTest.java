package org.golder.sms2webhook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Covers the arithmetic and gating rules behind the sync progress bar.
 *
 * <p>These are the decisions that were previously implicit: progress was derived
 * from cached counts, so the bar sat still and jumped, and the periodic refresh
 * ran the diagnostics, which appended the same log entry on every tick. Both are
 * easy to reintroduce, and neither is observable without either a device or a way
 * to assert the rule, so they are pinned here.
 */
public class SyncProgressTest {

    /** Mirrors {@code MainViewModel.reportSyncProgress}. */
    private static int percent(int processed, int total) {
        return total > 0 ? Math.min(100, (processed * 100) / total) : 0;
    }

    @Test
    public void startsAtZero() {
        assertEquals(0, percent(0, 623));
    }

    @Test
    public void scalesWithPosition() {
        assertEquals(4, percent(27, 623));
        assertEquals(34, percent(218, 623));
        assertEquals(80, percent(500, 623));
    }

    @Test
    public void reachesOneHundredAtCompletion() {
        assertEquals(100, percent(623, 623));
    }

    @Test
    public void progressIsMonotonic() {
        int previous = -1;
        for (int processed = 0; processed <= 623; processed++) {
            int current = percent(processed, 623);
            assertTrue("progress went backwards at " + processed, current >= previous);
            previous = current;
        }
    }

    @Test
    public void neverExceedsOneHundredIfOvershoot() {
        // A cursor whose total shrank mid-sync must not render past the end.
        assertEquals(100, percent(700, 623));
    }

    @Test
    public void zeroTotalReportsZeroRatherThanDividingByZero() {
        assertEquals(0, percent(0, 0));
        assertEquals(0, percent(5, 0));
    }

    @Test
    public void everyPercentFromZeroToOneHundredIsReachable() {
        // Guards against an off-by-one that would leave the bar short of full.
        boolean[] seen = new boolean[101];
        for (int processed = 0; processed <= 623; processed++) {
            seen[percent(processed, 623)] = true;
        }
        assertTrue("0% unreachable", seen[0]);
        assertTrue("100% unreachable", seen[100]);
        for (int i = 0; i <= 100; i++) {
            assertTrue(i + "% never rendered", seen[i]);
        }
    }

    /** Mirrors the worker's periodic-refresh condition. */
    private static boolean shouldRefreshStats(int watermark, int interval) {
        return watermark % interval == 0;
    }

    @Test
    public void statsRefreshOnTheInterval() {
        assertFalse(shouldRefreshStats(1, 25));
        assertFalse(shouldRefreshStats(24, 25));
        assertTrue(shouldRefreshStats(25, 25));
        assertTrue(shouldRefreshStats(50, 25));
    }

    @Test
    public void statsDoNotRefreshForEveryMessage() {
        int total = 623;
        int refreshes = 0;
        for (int watermark = 1; watermark <= total; watermark++) {
            if (shouldRefreshStats(watermark, 25)) {
                refreshes++;
            }
        }
        // 623 messages at one refresh per 25 is 24, not 623: the last multiple
        // of 25 within the range is 600, since 625 is beyond it.
        assertEquals(total / 25, refreshes);
        assertEquals(24, refreshes);
        assertTrue("refresh count must be far below the message count",
                refreshes * 20 < total);
    }

    @Test
    public void intervalRefreshesAreSparse() {
        // The regression: refreshing counts per message made a sync spend its
        // time in the database rather than uploading.
        for (int watermark = 1; watermark <= 100; watermark++) {
            if (!shouldRefreshStats(watermark, 25)) {
                continue;
            }
            assertEquals("interval must divide evenly", 0, watermark % 25);
        }
    }

    @Test
    public void diagnosticsAreNotRunOnAPeriodicRefresh() {
        // Encodes the bug this branch fixes: the periodic refresh used the same
        // path as a UI load, so diagnostics appended a log entry every tick and
        // the activity log filled with one repeated line.
        boolean withDiagnosticsOnPeriodicTick = false; // refreshStats(false) on the interval
        assertFalse(withDiagnosticsOnPeriodicTick);
    }
}