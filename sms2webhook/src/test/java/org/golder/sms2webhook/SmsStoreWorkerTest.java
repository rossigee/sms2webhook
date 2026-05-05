package org.golder.sms2webhook;

import android.database.Cursor;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SmsStoreWorkerTest {

    @Test
    public void encodeMessage_singleColumn_encodedCorrectly() {
        Cursor cursor = mock(Cursor.class);
        when(cursor.getColumnNames()).thenReturn(new String[]{"address"});
        when(cursor.getColumnIndex("address")).thenReturn(0);
        when(cursor.getString(0)).thenReturn("+1234567890");

        JSONObject result = SmsStoreWorker.encodeMessage(cursor);

        assertEquals(1, result.length());
        assertEquals("+1234567890", result.optString("address"));
    }

    @Test
    public void encodeMessage_multipleColumns_allEncoded() {
        Cursor cursor = mock(Cursor.class);
        when(cursor.getColumnNames()).thenReturn(new String[]{"address", "body", "date"});
        when(cursor.getColumnIndex("address")).thenReturn(0);
        when(cursor.getColumnIndex("body")).thenReturn(1);
        when(cursor.getColumnIndex("date")).thenReturn(2);
        when(cursor.getString(0)).thenReturn("+1234567890");
        when(cursor.getString(1)).thenReturn("Hello world");
        when(cursor.getString(2)).thenReturn("1234567890000");

        JSONObject result = SmsStoreWorker.encodeMessage(cursor);

        assertEquals(3, result.length());
        assertEquals("+1234567890", result.optString("address"));
        assertEquals("Hello world", result.optString("body"));
        assertEquals("1234567890000", result.optString("date"));
    }

    @Test
    public void encodeMessage_noColumns_returnsEmptyObject() {
        Cursor cursor = mock(Cursor.class);
        when(cursor.getColumnNames()).thenReturn(new String[]{});

        JSONObject result = SmsStoreWorker.encodeMessage(cursor);

        assertEquals(0, result.length());
    }

    @Test
    public void encodeMessage_negativeColumnIndex_skipsColumn() {
        // getColumnIndex returns -1 when column not found — should be skipped
        Cursor cursor = mock(Cursor.class);
        when(cursor.getColumnNames()).thenReturn(new String[]{"unknown"});
        when(cursor.getColumnIndex("unknown")).thenReturn(-1);

        JSONObject result = SmsStoreWorker.encodeMessage(cursor);

        assertEquals(0, result.length());
    }

    @Test
    public void encodeMessage_nullColumnValue_doesNotThrow() {
        Cursor cursor = mock(Cursor.class);
        when(cursor.getColumnNames()).thenReturn(new String[]{"body"});
        when(cursor.getColumnIndex("body")).thenReturn(0);
        when(cursor.getString(0)).thenReturn(null);

        assertNotNull(SmsStoreWorker.encodeMessage(cursor));
    }

    @Test
    public void encodeMessage_typicalSmsCursor_producesExpectedShape() {
        Cursor cursor = mock(Cursor.class);
        String[] cols = {"_id", "address", "body", "date", "type", "read"};
        when(cursor.getColumnNames()).thenReturn(cols);
        for (int i = 0; i < cols.length; i++) {
            when(cursor.getColumnIndex(cols[i])).thenReturn(i);
        }
        when(cursor.getString(0)).thenReturn("42");
        when(cursor.getString(1)).thenReturn("+447700900000");
        when(cursor.getString(2)).thenReturn("Test message");
        when(cursor.getString(3)).thenReturn("1700000000000");
        when(cursor.getString(4)).thenReturn("1");
        when(cursor.getString(5)).thenReturn("1");

        JSONObject result = SmsStoreWorker.encodeMessage(cursor);

        assertEquals(cols.length, result.length());
        assertEquals("+447700900000", result.optString("address"));
        assertEquals("Test message", result.optString("body"));
    }
}
