package org.golder.sms2webhook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * Covers the cache consistency checks and the rule for reporting them.
 *
 * <p>Diagnostics run on every statistics load: every activity launch, every Clear
 * Cache and every sync exit. Logging each finding each time filled the activity log
 * with the same line — four copies of "624 messages have no cache entry" were
 * visible at rest on a real device.
 */
public class CacheDiagnosticsTest {

    private static List<CacheDiagnostics.Finding> evaluate(int storeCount, int uniqueCount,
                                                           int totalEntries, int sent, int refused) {
        return CacheDiagnostics.evaluate(storeCount, uniqueCount, totalEntries, sent, refused);
    }

    @Test
    public void aFreshInstallReportsEveryMessageAsUncached() {
        List<CacheDiagnostics.Finding> findings = evaluate(624, 0, 0, 0, 0);

        assertEquals(1, findings.size());
        assertTrue(findings.get(0).message.contains("624 messages have no cache entry"));
    }

    @Test
    public void aConsistentCacheReportsNothing() {
        assertTrue(evaluate(10, 10, 10, 10, 0).isEmpty());
    }

    @Test
    public void duplicateRowsAreReported() {
        // Ten held, eight distinct hashes, twelve rows: both a duplicate-row
        // finding and an uncached-message finding are true here.
        List<CacheDiagnostics.Finding> findings = evaluate(10, 8, 12, 8, 0);

        assertEquals(2, findings.size());
        assertTrue(findings.stream().anyMatch(f -> f.message.contains("duplicate cache entries")));
        assertTrue(findings.stream().anyMatch(f -> f.message.contains("no cache entry")));
    }

    @Test
    public void aCountedMismatchIsReported() {
        // Ten unique rows, but only nine accounted for by sent + refused.
        List<CacheDiagnostics.Finding> findings = evaluate(10, 10, 10, 9, 0);

        assertEquals(1, findings.size());
        assertTrue(findings.get(0).message.contains("Cache inconsistency"));
    }

    @Test
    public void nothingIsReportedTwiceWhenNothingChanged() {
        // The defect this fixes: identical input produced an identical log line on
        // every load, so the log filled with copies of one finding.
        List<CacheDiagnostics.Finding> first = evaluate(624, 0, 0, 0, 0);

        assertEquals(1, CacheDiagnostics.sinceReported(null, first).size());

        for (int load = 0; load < 20; load++) {
            assertTrue("a repeat load must report nothing",
                    CacheDiagnostics.sinceReported(first, evaluate(624, 0, 0, 0, 0)).isEmpty());
        }
    }

    @Test
    public void aChangedCountIsReportedAgain() {
        // The count is part of the finding, so a progress sync reports its progress
        // rather than going quiet on a stale number.
        List<CacheDiagnostics.Finding> before = evaluate(624, 0, 0, 0, 0);
        List<CacheDiagnostics.Finding> after = evaluate(600, 24, 24, 24, 0);

        List<CacheDiagnostics.Finding> fresh = CacheDiagnostics.sinceReported(before, after);

        assertEquals(1, fresh.size());
        // 600 held against 24 recorded, so 576 are still uncached.
        assertTrue(fresh.get(0).message.contains("576 messages have no cache entry"));
    }

    @Test
    public void aClearedCacheResetsSoFindingsReportAgain() {
        // Nothing wrong, then wrong again: the caller stores null for a clean
        // result, so a recurrence is not suppressed forever.
        List<CacheDiagnostics.Finding> clean = evaluate(10, 10, 10, 10, 0);
        assertTrue(clean.isEmpty());

        List<CacheDiagnostics.Finding> dirty = evaluate(12, 10, 10, 10, 0);
        assertEquals(1, CacheDiagnostics.sinceReported(null, dirty).size());
    }

    @Test
    public void onlyTheNewFindingIsReportedWhenSeveralExist() {
        List<CacheDiagnostics.Finding> before = evaluate(10, 10, 10, 10, 0);
        List<CacheDiagnostics.Finding> after = evaluate(12, 10, 14, 10, 0);

        List<CacheDiagnostics.Finding> fresh = CacheDiagnostics.sinceReported(before, after);

        assertEquals(2, fresh.size());
        assertTrue(fresh.stream().anyMatch(f -> f.message.contains("no cache entry")));
        assertTrue(fresh.stream().anyMatch(f -> f.message.contains("duplicate cache entries")));
    }

    @Test
    public void findingsCompareByValueNotIdentity() {
        List<CacheDiagnostics.Finding> a = evaluate(624, 0, 0, 0, 0);
        List<CacheDiagnostics.Finding> b = evaluate(624, 0, 0, 0, 0);

        assertTrue("a new but equal finding must count as already reported",
                CacheDiagnostics.sinceReported(a, b).isEmpty());
    }

    @Test
    public void theWarningTypeIsPreservedSoTheLogStillColourCodes() {
        List<CacheDiagnostics.Finding> findings = evaluate(10, 8, 12, 8, 0);
        assertEquals(MainViewModel.LogEntry.Type.WARNING, findings.get(0).type);
    }
}