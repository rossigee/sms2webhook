package org.golder.sms2webhook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests for parsing the setup QR payload produced by the Odoo SMS Device form.
 *
 * <p>These are the cases that decide whether a scanned code silently overwrites
 * good settings with something wrong, so the negative cases matter as much as the
 * happy path.
 */
public class SetupPayloadTest {

    private static final String VALID =
            "{\"v\":1,\"url\":\"https://odoo.golder.org/sms/upload\","
                    + "\"key\":\"abc123secret\",\"device\":\"Pixel 8\"}";

    @Test
    public void parsesTheServersPayload() {
        SetupPayload.ParseResult r = SetupPayload.parse(VALID);
        assertTrue(r.getError(), r.isSuccess());
        assertEquals("https://odoo.golder.org/sms/upload", r.getPayload().getUrl());
        assertEquals("abc123secret", r.getPayload().getKey());
    }

    @Test
    public void toleratesSurroundingWhitespace() {
        SetupPayload.ParseResult r = SetupPayload.parse("\n  " + VALID + "  \t");
        assertTrue(r.getError(), r.isSuccess());
    }

    @Test
    public void trimsUrlAndKey() {
        SetupPayload.ParseResult r = SetupPayload.parse(
                "{\"v\":1,\"url\":\"  https://host/sms/upload  \",\"key\":\"  k  \"}");
        assertTrue(r.getError(), r.isSuccess());
        assertEquals("https://host/sms/upload", r.getPayload().getUrl());
        assertEquals("k", r.getPayload().getKey());
    }

    @Test
    public void deviceNameIsOptional() {
        SetupPayload.ParseResult r = SetupPayload.parse(
                "{\"v\":1,\"url\":\"https://host/sms/upload\",\"key\":\"k\"}");
        assertTrue(r.getError(), r.isSuccess());
    }

    @Test
    public void rejectsEmptyScan() {
        assertFalse(SetupPayload.parse(null).isSuccess());
        assertFalse(SetupPayload.parse("").isSuccess());
        assertFalse(SetupPayload.parse("   ").isSuccess());
    }

    @Test
    public void rejectsNonJson() {
        SetupPayload.ParseResult r = SetupPayload.parse("https://host/sms/upload?key=abc");
        assertFalse(r.isSuccess());
        assertNotNull(r.getError());
    }

    @Test
    public void rejectsMissingVersion() {
        SetupPayload.ParseResult r = SetupPayload.parse(
                "{\"url\":\"https://host/sms/upload\",\"key\":\"k\"}");
        assertFalse(r.isSuccess());
        assertTrue(r.getError().contains("version"));
    }

    @Test
    public void rejectsFutureVersion() {
        SetupPayload.ParseResult r = SetupPayload.parse(
                "{\"v\":2,\"url\":\"https://host/sms/upload\",\"key\":\"k\"}");
        assertFalse(r.isSuccess());
        assertTrue(r.getError().contains("version"));
    }

    @Test
    public void rejectsMissingUrl() {
        SetupPayload.ParseResult r = SetupPayload.parse("{\"v\":1,\"key\":\"k\"}");
        assertFalse(r.isSuccess());
        assertTrue(r.getError().contains("URL"));
    }

    @Test
    public void rejectsMissingKey() {
        SetupPayload.ParseResult r = SetupPayload.parse(
                "{\"v\":1,\"url\":\"https://host/sms/upload\"}");
        assertFalse(r.isSuccess());
        assertTrue(r.getError().contains("API key"));
    }

    @Test
    public void rejectsBlankKey() {
        SetupPayload.ParseResult r = SetupPayload.parse(
                "{\"v\":1,\"url\":\"https://host/sms/upload\",\"key\":\"   \"}");
        assertFalse(r.isSuccess());
    }

    @Test
    public void rejectsCleartextEndpoint() {
        // The server never sends one, but a scanned code is untrusted input and
        // must not be able to downgrade the transport.
        SetupPayload.ParseResult r = SetupPayload.parse(
                "{\"v\":1,\"url\":\"http://host/sms/upload\",\"key\":\"k\"}");
        assertFalse(r.isSuccess());
        assertTrue(r.getError().contains("HTTPS"));
    }

    @Test
    public void rejectsNonUrlEndpoint() {
        SetupPayload.ParseResult r = SetupPayload.parse(
                "{\"v\":1,\"url\":\"javascript:alert(1)\",\"key\":\"k\"}");
        assertFalse(r.isSuccess());
    }

    @Test
    public void rejectsFileEndpoint() {
        SetupPayload.ParseResult r = SetupPayload.parse(
                "{\"v\":1,\"url\":\"file:///etc/passwd\",\"key\":\"k\"}");
        assertFalse(r.isSuccess());
    }

    @Test
    public void preservesUrlPathAndQuery() {
        SetupPayload.ParseResult r = SetupPayload.parse(
                "{\"v\":1,\"url\":\"https://host:8443/a/b?c=d&e=f\",\"key\":\"k\"}");
        assertTrue(r.getError(), r.isSuccess());
        assertEquals("https://host:8443/a/b?c=d&e=f", r.getPayload().getUrl());
    }
}
