package org.golder.sms2webhook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Covers the activity log's size cap and snapshot behaviour.
 *
 * <p>Two things were wrong here. The log grew for as long as the process lived,
 * because a sync of a large inbox appends an entry per message. And the list was
 * both mutated and copied from the main thread and from a background pool at the
 * same time, which throws: {@code ArrayList.toArray} reads the backing array by
 * index, so copying while another thread inserts is an out-of-bounds access.
 *
 * <p>The cap and the copy are pinned here because both are invisible without
 * either a device or a way to assert them.
 */
public class ActivityLogTest {

    private static MainViewModel.LogEntry entry(String message) {
        return new MainViewModel.LogEntry(message, MainViewModel.LogEntry.Type.INFO, 0L);
    }

    @Test
    public void aShortLogIsCopiedWhole() {
        List<MainViewModel.LogEntry> log = new ArrayList<>();
        log.add(entry("a"));
        log.add(entry("b"));

        List<MainViewModel.LogEntry> snapshot = MainViewModel.snapshotOf(log);

        assertEquals(2, snapshot.size());
        assertEquals("a", snapshot.get(0).message);
        assertEquals("b", snapshot.get(1).message);
    }

    @Test
    public void anEmptyLogIsHandled() {
        assertEquals(0, MainViewModel.snapshotOf(new ArrayList<>()).size());
    }

    @Test
    public void theLogIsCapped() {
        List<MainViewModel.LogEntry> log = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            log.add(entry("message " + i));
        }

        assertEquals(1000, MainViewModel.snapshotOf(log).size());
    }

    @Test
    public void cappingKeepsTheNewestEntries() {
        // Newest first, so a full log must drop the tail.
        List<MainViewModel.LogEntry> log = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            log.add(0, entry("message " + i));
        }

        List<MainViewModel.LogEntry> snapshot = MainViewModel.snapshotOf(log);

        assertEquals("newest entry must be kept", "message 4999", snapshot.get(0).message);
        assertEquals("oldest surviving entry must be at the tail", "message 4000",
                snapshot.get(snapshot.size() - 1).message);
    }

    @Test
    public void exactlyAtTheCapIsNotTruncated() {
        List<MainViewModel.LogEntry> log = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            log.add(entry("message " + i));
        }
        assertEquals(1000, MainViewModel.snapshotOf(log).size());
    }

    @Test
    public void theSnapshotIsIndependentOfLaterMutation() {
        // The posted value must not change underneath the adapter, which holds it
        // until the next update. Sharing the list was how a rebind could read an
        // entry that had already been dropped.
        List<MainViewModel.LogEntry> log = new ArrayList<>();
        log.add(entry("a"));
        log.add(entry("b"));

        List<MainViewModel.LogEntry> snapshot = MainViewModel.snapshotOf(log);
        log.add(0, entry("c"));
        log.add(entry("d"));

        assertNotSame(log, snapshot);
        assertEquals(2, snapshot.size());
        assertEquals("a", snapshot.get(0).message);
    }

    @Test
    public void aSyncLongerThanTheCapStaysBounded() {
        // What a large inbox does: one entry per message, past the cap.
        List<MainViewModel.LogEntry> log = new ArrayList<>();
        int messages = 20000;
        for (int i = 0; i < messages; i++) {
            log.add(0, entry("[" + (i + 1) + " / " + messages + "]"));
            log = MainViewModel.snapshotOf(log);
        }
        assertTrue("log must not grow with the sync", log.size() <= 1000);
        assertEquals(1000, log.size());
    }
}