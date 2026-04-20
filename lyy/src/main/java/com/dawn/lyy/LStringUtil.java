package com.dawn.lyy;

/**
 * 字符串工具类
 * 仅保留项目实际使用的 hex 转换方法。
 */
class LStringUtil {

    private LStringUtil() {
    }

    /**
     * 字节数组转为十六进制字符串
     *
     * @param byteArray 字节数组
     * @return 大写的十六进制字符串
     */
    static String toHexString(byte[] byteArray) {
        if (byteArray == null || byteArray.length < 1)
            throw new IllegalArgumentException("byteArray must not be null or empty");

        final StringBuilder hexString = new StringBuilder(byteArray.length * 2);
        for (byte b : byteArray) {
            if ((b & 0xFF) < 0x10)
                hexString.append("0");
            hexString.append(Integer.toHexString(0xFF & b));
        }
        return hexString.toString().toUpperCase();
    }

    /**
     * 十六进制字符串转为字节数组
     *
     * @param hexString 十六进制字符串
     * @return 字节数组
     */
    static byte[] toByteArray(String hexString) {
        if (hexString == null || hexString.isEmpty())
            throw new IllegalArgumentException("hexString must not be null or empty");

        hexString = hexString.toUpperCase();
        final byte[] byteArray = new byte[hexString.length() / 2];
        int k = 0;
        for (int i = 0; i < byteArray.length; i++) {
            byte high = (byte) (Character.digit(hexString.charAt(k), 16) & 0xFF);
            byte low = (byte) (Character.digit(hexString.charAt(k + 1), 16) & 0xFF);
            byteArray[i] = (byte) (high << 4 | low & 0xFF);
            k += 2;
        }
        return byteArray;
    }
}
