package org.golder.sms2webhook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Covers what the deduplication digest is taken over.
 *
 * <p>The payload is the SMS provider's whole row, which means it carries fields
 * that change after delivery: {@code read} and {@code seen} flip when a message is
 * opened, {@code status} and {@code error_code} move as it sends, and {@code _id} is
 * a row id the provider assigns per install. Hashing that whole row produced a
 * different digest for a message that had already been sent, so every message that
 * was ever read re-uploaded on the next sync, and no message could be recognised on
 * a restored device.
 *
 * <p>The collector dedups server-side on address/date_sent/body/thread_id only, and
 * its test suite documents this property explicitly, so these are the same four
 * fields.
 */
public class MessageDigestTest {

    /** A payload as {@code SmsStoreWorker.encodeMessage} builds it: all strings. */
    private static JSONObject payload() throws JSONException {
        return new JSONObject()
                .put("_id", "42")
                .put("thread_id", "2")
                .put("address", "6505551212")
                .put("date", "1724154171042")
                .put("date_sent", "1724154170000")
                .put("body", "Your code is 123456")
                .put("read", "0")
                .put("seen", "0")
                .put("status", "-1")
                .put("error_code", "0")
                .put("type", "1");
    }

    private static String digest(JSONObject message) throws Exception {
        return DigestUtil.getMessageDigest(message);
    }

    @Test
    public void theSameMessageDigestsTheSame() throws Exception {
        assertEquals(digest(payload()), digest(payload()));
    }

    @Test
    public void readingAMessageDoesNotChangeItsDigest() throws Exception {
        // The regression that made every read message re-upload. read and seen are
        // exactly what the provider flips when the message is opened.
        JSONObject before = payload();
        JSONObject after = payload();
        after.put("read", "1");
        after.put("seen", "1");
        after.put("status", "1");
        after.put("error_code", "1");

        assertEquals("a delivered-but-read message must still be recognised",
                digest(before), digest(after));
    }

    @Test
    public void unrelatedProviderColumnsDoNotAffectTheDigest() throws Exception {
        // Anything the provider may add, remove or reformat between releases.
        JSONObject other = payload();
        other.put("creator", "com.google.android.apps.messaging");
        other.put("reply_path_present", "0");
        other.put("locked", "0");
        other.put("sub_id", "2");
        other.put("person", "-1");

        assertEquals(digest(payload()), digest(other));
    }

    @Test
    public void aRenumberedRowIdIsStillTheSameMessage() throws Exception {
        // _id is assigned per install, so a reinstall renumbers every row. The old
        // whole-payload digest changed with it and the entire inbox re-uploaded as
        // new. The identity digest does not depend on it at all.
        JSONObject original = payload();
        JSONObject afterRestore = payload();
        afterRestore.put("_id", "9001");

        assertEquals(digest(original), digest(afterRestore));
    }

    @Test
    public void aRenumberedThreadIsADifferentMessage() throws Exception {
        // thread_id is one of the four identity fields, matching the collector's own
        // _compute_hash. So it is not restore-stable either, and the digest cannot
        // survive a provider that renumbers threads on reinstall. Claiming otherwise
        // would be wrong: closing that gap means dropping thread_id from both this
        // digest and the collector's, which is a backend change.
        JSONObject one = payload();
        one.put("thread_id", "2");
        JSONObject two = payload();
        two.put("thread_id", "77");

        assertNotEquals(digest(one), digest(two));
    }

    @Test
    public void theProviderRowIdAloneCannotDistinguishMessages() throws Exception {
        JSONObject one = payload();
        one.put("_id", "1");
        JSONObject two = payload();
        two.put("_id", "2");
        assertEquals(digest(one), digest(two));
    }

    @Test
    public void aDifferentBodyIsADifferentMessage() throws Exception {
        JSONObject other = payload();
        other.put("body", "Your code is 654321");
        assertNotEquals(digest(payload()), digest(other));
    }

    @Test
    public void aDifferentSenderIsADifferentMessage() throws Exception {
        JSONObject other = payload();
        other.put("address", "6505559999");
        assertNotEquals(digest(payload()), digest(other));
    }

    @Test
    public void aDifferentThreadIsADifferentMessage() throws Exception {
        JSONObject other = payload();
        other.put("thread_id", "99");
        assertNotEquals(digest(payload()), digest(other));
    }

    @Test
    public void aDifferentSentTimeIsADifferentMessage() throws Exception {
        JSONObject other = payload();
        other.put("date_sent", "1724154170001");
        assertNotEquals(digest(payload()), digest(other));
    }

    @Test
    public void anIdenticalResendIsTheSameMessage() throws Exception {
        // The collector dedups on the same four fields, so it answers
        // X-Already-Existed here. The app must agree, or every provider redelivery
        // becomes another round trip.
        assertEquals(digest(payload()), digest(payload()));
    }

    @Test
    public void aBodyCannotForgeAFieldBoundary() throws Exception {
        // Length-prefixed encoding, so a crafted body cannot collide with a
        // different field layout. A body is arbitrary bytes from an SMS sender.
        JSONObject forger = payload();
        forger.put("body", "x\nbody:1:evil");
        JSONObject other = payload();
        other.put("body", "x");
        other.put("thread_id", "body:1:evil");

        assertNotEquals("a body must not be able to fake another field",
                digest(forger), digest(other));
    }

    @Test
    public void aMissingFieldReadsAsEmptyRatherThanFailing() throws Exception {
        // Some providers do not expose every column. A device missing date_sent
        // must still sync rather than abandon every message.
        JSONObject sparse = new JSONObject()
                .put("address", "6505551212")
                .put("body", "hello");

        String d = digest(sparse);
        assertEquals(64, d.length());
        assertEquals("a sparse payload must be stable", d, digest(sparse));
    }

    @Test
    public void aFullyEmptyPayloadStillDigests() throws Exception {
        assertEquals(64, digest(new JSONObject()).length());
    }

    @Test
    public void theDigestIsLowercaseHexOfTheRightWidth() throws Exception {
        String d = digest(payload());
        assertEquals("SHA-256 is 32 bytes, so 64 hex characters", 64, d.length());
        assertTrue("must be lowercase hex: " + d, d.matches("[0-9a-f]{64}"));
    }

    @Test
    public void fieldOrderInThePayloadDoesNotMatter() throws Exception {
        // JSONObject iteration order is not specified, so the digest must not be
        // built by iterating the payload.
        JSONObject a = new JSONObject().put("body", "hi").put("address", "6505551212");
        JSONObject b = new JSONObject().put("address", "6505551212").put("body", "hi");
        assertEquals(digest(a), digest(b));
    }
}
