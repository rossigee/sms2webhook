package org.golder.sms2webhook;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;

import static org.junit.Assert.*;

public class DigestUtilTest {

    @Test
    public void sha256_knownInput_correctHash() throws NoSuchAlgorithmException {
        String result = DigestUtil.getHexSHA256Hash("hello".getBytes(StandardCharsets.UTF_8));
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", result);
    }

    @Test
    public void sha256_emptyInput_correctHash() throws NoSuchAlgorithmException {
        String result = DigestUtil.getHexSHA256Hash(new byte[0]);
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", result);
    }

    @Test
    public void sha256_sameInput_consistentOutput() throws NoSuchAlgorithmException {
        byte[] input = "consistent".getBytes(StandardCharsets.UTF_8);
        assertEquals(DigestUtil.getHexSHA256Hash(input), DigestUtil.getHexSHA256Hash(input));
    }

    @Test
    public void sha256_differentInputs_differentHashes() throws NoSuchAlgorithmException {
        String h1 = DigestUtil.getHexSHA256Hash("message_a".getBytes(StandardCharsets.UTF_8));
        String h2 = DigestUtil.getHexSHA256Hash("message_b".getBytes(StandardCharsets.UTF_8));
        assertNotEquals(h1, h2);
    }

    @Test
    public void sha256_result_is64LowercaseHexChars() throws NoSuchAlgorithmException {
        String result = DigestUtil.getHexSHA256Hash("test".getBytes(StandardCharsets.UTF_8));
        assertEquals(64, result.length());
        assertTrue(result.matches("[0-9a-f]{64}"));
    }

    @Test
    public void sha256_jsonPayload_hashesCorrectly() throws NoSuchAlgorithmException {
        // Simulates hashing an SMS JSON payload — same content must always yield same hash
        String payload = "{\"address\":\"+1234567890\",\"body\":\"Hello\",\"date\":\"1234567890\"}";
        String h1 = DigestUtil.getHexSHA256Hash(payload.getBytes(StandardCharsets.UTF_8));
        String h2 = DigestUtil.getHexSHA256Hash(payload.getBytes(StandardCharsets.UTF_8));
        assertEquals(64, h1.length());
        assertEquals(h1, h2);
    }
}
