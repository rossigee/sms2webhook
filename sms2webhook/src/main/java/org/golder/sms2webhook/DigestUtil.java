package org.golder.sms2webhook;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DigestUtil {

    /**
     * The fields that identify a message, and nothing else.
     *
     * <p>Deliberately narrower than the payload. The payload is the provider's whole
     * row, which carries fields that change after delivery: {@code read} and
     * {@code seen} flip when the message is opened, {@code status} and
     * {@code error_code} move as it is sent, and {@code _id} is a row id the
     * provider assigns per install. Hashing the whole row meant any of those
     * produced a different digest for a message that had already been sent, so the
     * cache missed and the message was uploaded again. Every message that was ever
     * read therefore re-uploaded on the following sync, and did so forever.
     *
     * <p>These four are the message's identity rather than its delivery state, so
     * the digest is stable for as long as the provider keeps the row. They are also
     * the same four the collector hashes server-side
     * ({@code sms_message.py:_compute_hash}), so the two agree on what "the same
     * message" means and the app's cache and the server's dedup line up.
     *
     * <p>Two genuinely different messages sharing all four are indistinguishable
     * here. The server already treats them as one, enforced by a unique constraint
     * on its own hash, so this does not introduce a collision the backend does not
     * already resolve.
     */
    private static final String[] IDENTITY_FIELDS = {"address", "date_sent", "body", "thread_id"};

    public static String getHexSHA256Hash(byte[] msg) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(msg);
        return getHexHash(md.digest());
    }

    /**
     * Digest identifying a message for deduplication.
     *
     * <p>Absent fields read as empty rather than failing, so a provider that does
     * not expose one of them still yields a usable digest instead of abandoning the
     * sync.
     */
    public static String getMessageDigest(JSONObject message) throws NoSuchAlgorithmException {
        StringBuilder identity = new StringBuilder();
        for (String field : IDENTITY_FIELDS) {
            appendField(identity, field, message.optString(field, ""));
        }
        return getHexSHA256Hash(identity.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Appends one length-prefixed field.
     *
     * <p>Length-prefixed rather than separated by a delimiter, so no value can
     * forge a field boundary and make two different messages digest identically.
     * A message body is attacker-controlled in the sense that an SMS can contain
     * any bytes, including a newline followed by a field name.
     */
    private static void appendField(StringBuilder out, String name, String value) {
        out.append(name).append(':').append(value.length()).append(':').append(value).append(';');
    }

    private static String getHexHash(byte[] digest) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : digest) {
            String hexByte = Integer.toHexString(0xff & b);
            if (hexByte.length() == 1) {
                hexString.append('0').append(hexByte);
            } else {
                hexString.append(hexByte);
            }
        }
        return hexString.toString();
    }
}