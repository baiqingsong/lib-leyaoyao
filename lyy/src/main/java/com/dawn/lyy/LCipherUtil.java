package com.dawn.lyy;

import android.annotation.SuppressLint;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * 加密工具类
 * 仅保留项目实际使用的 AES 加解密方法。
 */
class LCipherUtil {

    private LCipherUtil() {
    }

    /**
     * AES加密
     *
     * @param message  加密的字符串
     * @param passWord 加密的密码（hex字符串）
     * @return AES加密后的hex字符串
     */
    static String encryptAES(String message, String passWord)
            throws GeneralSecurityException, UnsupportedEncodingException {
        return LStringUtil.toHexString(encryptAES(message.getBytes("UTF-8"), LStringUtil.toByteArray(passWord)));
    }

    /**
     * AES解密
     *
     * @param message  需要解密的hex字符串
     * @param passWord 解密的密码（hex字符串）
     * @return AES解密后的字符串
     */
    static String decryptAES(String message, String passWord)
            throws GeneralSecurityException {
        return new String(decryptAES(LStringUtil.toByteArray(message), LStringUtil.toByteArray(passWord)));
    }

    @SuppressLint("GetInstance")
    private static byte[] encryptAES(byte[] source, byte[] rawKeyData)
            throws GeneralSecurityException {
        SecretKeySpec key = new SecretKeySpec(rawKeyData, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(source);
    }

    @SuppressLint("GetInstance")
    private static byte[] decryptAES(byte[] data, byte[] rawKeyData)
            throws GeneralSecurityException {
        SecretKeySpec key = new SecretKeySpec(rawKeyData, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, key);
        return cipher.doFinal(data);
    }
}
