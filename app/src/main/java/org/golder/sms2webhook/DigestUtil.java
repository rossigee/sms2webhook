package org.golder.sms2webhook;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DigestUtil {
    private static final String TAG = DigestUtil.class.getSimpleName();

    public static String getHexSHA256Hash(byte[] msg) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(msg);
        return getHexHash(md.digest());
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