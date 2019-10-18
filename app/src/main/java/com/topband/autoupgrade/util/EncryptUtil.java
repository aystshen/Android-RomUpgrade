package com.topband.autoupgrade.util;

import android.annotation.SuppressLint;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by ayst.shen@foxmail.com on 17/8/15.
 */
public class EncryptUtil {
    private static final String AES_TRANSFORMATION = "AES/ECB/PKCS5Padding";
    private static final String ASE_ALGORITHM = "AES";
    private final static String HEX = "0123456789abcdef";

    public static String getMD5HexMsg(String data) throws NoSuchAlgorithmException {
        MessageDigest md5Digest = MessageDigest.getInstance("MD5");
        md5Digest.update(data.getBytes());
        return new BigInteger(1, md5Digest.digest()).toString(16);
    }

    public static String encryptAES(String seed, String cleartext) throws Exception {
        byte[] rawKey = seed.getBytes(StandardCharsets.UTF_8);
        byte[] result = cleartext.getBytes(StandardCharsets.UTF_8);
        return toHex(encodeAES(rawKey, result));
    }

    public static String decryptAES(String seed, String encrypted) throws Exception {
        byte[] rawKey = seed.getBytes(StandardCharsets.UTF_8);
        byte[] enc = toByte(encrypted);
        byte[] result = decodeAES(rawKey, enc);
        return new String(result);
    }

    @SuppressLint("GetInstance")
    public static byte[] encodeAES(byte[] keyBytes, byte[] data) throws Exception {
        Cipher encodeCipher = Cipher.getInstance(AES_TRANSFORMATION);
        SecretKeySpec key = new SecretKeySpec(keyBytes, ASE_ALGORITHM);
        encodeCipher.init(Cipher.ENCRYPT_MODE, key);
        return encodeCipher.doFinal(data);
    }

    @SuppressLint("GetInstance")
    public static byte[] decodeAES(byte[] keyBytes, byte[] data) throws Exception {
        Cipher encodeCipher = Cipher.getInstance(AES_TRANSFORMATION);
        SecretKeySpec key = new SecretKeySpec(keyBytes, ASE_ALGORITHM);
        encodeCipher.init(Cipher.DECRYPT_MODE, key);
        return encodeCipher.doFinal(data);
    }

    private static String toHex(byte[] buf) {
        if (buf == null)
            return "";
        StringBuffer result = new StringBuffer(2 * buf.length);
        for (int i = 0; i < buf.length; i++) {
            appendHex(result, buf[i]);
        }
        return result.toString();
    }

    public static String toHex(String txt) {
        return toHex(txt.getBytes());
    }

    public static String fromHex(String hex) {
        return new String(toByte(hex));
    }

    public static byte[] toByte(String hexString) {
        int len = hexString.length() / 2;
        byte[] result = new byte[len];
        for (int i = 0; i < len; i++)
            result[i] = Integer.valueOf(hexString.substring(2 * i, 2 * i + 2), 16).byteValue();
        return result;
    }

    private static void appendHex(StringBuffer sb, byte b) {
        sb.append(HEX.charAt((b >> 4) & 0x0f)).append(HEX.charAt(b & 0x0f));
    }

}

