package org.golder.sms2webhook;

import org.junit.Test;

import static org.junit.Assert.*;

public class LogEntryTest {

    @Test
    public void logEntry_storesAllFields() {
        long ts = 1234567890L;
        MainViewModel.LogEntry entry = new MainViewModel.LogEntry("test message", MainViewModel.LogEntry.Type.INFO, ts);
        assertEquals("test message", entry.message);
        assertEquals(MainViewModel.LogEntry.Type.INFO, entry.type);
        assertEquals(ts, entry.timestamp);
    }

    @Test
    public void logEntry_allTypesConstructible() {
        for (MainViewModel.LogEntry.Type type : MainViewModel.LogEntry.Type.values()) {
            MainViewModel.LogEntry entry = new MainViewModel.LogEntry("msg", type, 0L);
            assertEquals(type, entry.type);
        }
    }

    @Test
    public void logEntry_exactlyFourTypes() {
        assertEquals(4, MainViewModel.LogEntry.Type.values().length);
    }

    @Test
    public void logEntry_allExpectedTypesPresent() {
        boolean hasInfo = false, hasSuccess = false, hasWarning = false, hasError = false;
        for (MainViewModel.LogEntry.Type t : MainViewModel.LogEntry.Type.values()) {
            switch (t) {
                case INFO:    hasInfo = true;    break;
                case SUCCESS: hasSuccess = true; break;
                case WARNING: hasWarning = true; break;
                case ERROR:   hasError = true;   break;
            }
        }
        assertTrue("Missing INFO type",    hasInfo);
        assertTrue("Missing SUCCESS type", hasSuccess);
        assertTrue("Missing WARNING type", hasWarning);
        assertTrue("Missing ERROR type",   hasError);
    }

    @Test
    public void logEntry_zeroTimestamp_stored() {
        MainViewModel.LogEntry entry = new MainViewModel.LogEntry("msg", MainViewModel.LogEntry.Type.ERROR, 0L);
        assertEquals(0L, entry.timestamp);
    }

    @Test
    public void logEntry_emptyMessage_stored() {
        MainViewModel.LogEntry entry = new MainViewModel.LogEntry("", MainViewModel.LogEntry.Type.INFO, 0L);
        assertEquals("", entry.message);
    }
}
