package com.hktools.parser;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Base58 {
    private static final String BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final BigInteger BASE = BigInteger.valueOf(58);

    // Map char → index cho decode nhanh
    private static final Map<Character, Integer> DECODE_MAP = new HashMap<>();
    static {
        for (int i = 0; i < BASE58_ALPHABET.length(); i++) {
            DECODE_MAP.put(BASE58_ALPHABET.charAt(i), i);
        }
    }
    /**
     * Base58 (Tron) → Hex string (giữ prefix 41)
     */
    public static String base58ToHex(String base58) {
        byte[] decoded = decodeBase58Check(base58);
        if (decoded == null || decoded.length != 21) {
            throw new IllegalArgumentException("Địa chỉ Base58 không hợp lệ");
        }

        return Hex.encodeHexString(decoded).toLowerCase();
    }
    // Decode Base58Check (kiểm tra checksum)
    private static byte[] decodeBase58Check(String input) {
        byte[] decoded = decodeBase58(input);
        if (decoded == null || decoded.length < 4) {
            return null;
        }

        byte[] data = Arrays.copyOfRange(decoded, 0, decoded.length - 4);
        byte[] checksum = Arrays.copyOfRange(decoded, decoded.length - 4, decoded.length);

        byte[] hash0 = DigestUtils.sha256(data);
        byte[] hash1 = DigestUtils.sha256(hash0);
        byte[] expected = Arrays.copyOfRange(hash1, 0, 4);

        if (!Arrays.equals(checksum, expected)) {
            throw new IllegalArgumentException("Checksum không khớp");
        }
        return data;
    }

    // Encode Base58Check (thêm checksum)
    private static String encodeBase58Check(byte[] input) {
        byte[] hash0 = DigestUtils.sha256(input);
        byte[] hash1 = DigestUtils.sha256(hash0);
        byte[] checksum = Arrays.copyOfRange(hash1, 0, 4);

        byte[] full = new byte[input.length + 4];
        System.arraycopy(input, 0, full, 0, input.length);
        System.arraycopy(checksum, 0, full, input.length, 4);

        return encodeBase58(full);
    }

    // Base58 decode thuần (BigInteger)
    private static byte[] decodeBase58(String input) {
        BigInteger bi = BigInteger.ZERO;
        for (char c : input.toCharArray()) {
            Integer val = DECODE_MAP.get(c);
            if (val == null) {
                throw new IllegalArgumentException("Ký tự Base58 không hợp lệ: " + c);
            }
            bi = bi.multiply(BASE).add(BigInteger.valueOf(val));
        }

        byte[] bytes = bi.toByteArray();
        // Xử lý leading zeros
        int leadingZeros = 0;
        for (char c : input.toCharArray()) {
            if (c == '1') leadingZeros++;
            else break;
        }

        byte[] result = new byte[bytes.length + leadingZeros];
        System.arraycopy(bytes, 0, result, leadingZeros, bytes.length);
        return result;
    }

    // Base58 encode thuần (BigInteger)
    private static String encodeBase58(byte[] input) {
        BigInteger value = new BigInteger(1, input);
        StringBuilder sb = new StringBuilder();

        while (value.compareTo(BigInteger.ZERO) > 0) {
            BigInteger[] divMod = value.divideAndRemainder(BASE);
            sb.insert(0, BASE58_ALPHABET.charAt(divMod[1].intValue()));
            value = divMod[0];
        }

        // Thêm '1' cho leading zero bytes
        for (byte b : input) {
            if (b == 0) {
                sb.insert(0, '1');
            } else {
                break;
            }
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        String base58Address = "TAivxotYZki8QHNmDseEUMK9RNmRmVEe7B";
        String hexAddress = base58ToHex(base58Address);
        System.out.println("Hex Address: " + hexAddress);
    }
}
