package org.golder.sms2webhook;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.database.SQLException;
import android.telephony.SmsMessage;
import android.util.Log;

import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.json.JSONException;

public class DigestUtil {
    private static final String TAG = DigestUtil.class.getSimpleName();

    @SuppressLint("Range")
    public static JSONObject encodeMessage(Cursor cursor) {
        JSONObject jsonObject = new JSONObject();

        if (cursor != null && cursor.moveToFirst()) {
            try {
                String[] columns = cursor.getColumnNames();
                for (String column : columns) {
                    jsonObject.put(column, cursor.getString( cursor.getColumnIndex(column)));
                }
            } catch (SQLException | JSONException e) {
                Log.e(TAG, "Error parsing cursor to JSONObject", e);
            }
        }

        return jsonObject;
    }

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

    public static JSONObject toJSON(SmsMessage msg) {
        JSONObject jsonObject = new JSONObject();

        // Assuming you want to include the following properties in the JSON output
        try {
            jsonObject.put("date", msg.getTimestampMillis());
            jsonObject.put("address", msg.getOriginatingAddress());
            jsonObject.put("body", msg.getUserData());

            // If the message contains multiple parts (e.g., MMS), add them as an array
            // TODO: support MMS
//            if (msg.getMessageType() == -1) { // MMS or other multi-part message types
//                JSONArray parts = new JSONArray();
//                for (int i = 0; i < msg.getMessagesCount(); i++) {
//                    SmsMessage part = msg.getMessageBody(i);
//                    JSONObject partJson = new JSONObject();
//                    partJson.put("date", part.getTimestampMillis());
//                    partJson.put("address", part.getOriginatingAddress());
//                    partJson.put("body", part.getUserData());
//                    parts.put(partJson);
//                }
//                jsonObject.put("parts", parts);
//            }
        } catch (JSONException e) {
            // Handle the exception if something goes wrong during JSON creation
            Log.e("SmsMessageToJson", "Error creating JSON: " + e.getMessage());
            return null;
        }

        return jsonObject;
    }
}