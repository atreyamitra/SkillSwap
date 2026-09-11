package com.skillswap.app.ledger.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * HMAC-SHA256 computation, used by the ledger to make each entry tamper-evident (see
 * {@code docs/INTEGRITY_AND_IDEMPOTENCY.md}).
 *
 * A fresh {@link Mac} instance is created on every call rather than reusing one shared
 * instance across threads. {@code Mac} is stateful (it accumulates bytes between
 * {@code update}/{@code doFinal} calls) and is documented as not safe for concurrent
 * use by multiple threads without external synchronization. Since HMAC computation
 * here is cheap relative to everything else the ledger does per transaction (a lock
 * acquisition, a HashMap update), instantiating fresh is simpler and strictly safer
 * than sharing one {@code Mac} behind a lock or a {@code ThreadLocal} pool — and it
 * sidesteps a real, common concurrency bug (silently corrupting MACs under
 * contention) rather than "fixing" it with more machinery.
 */
public final class HmacUtil {

    private static final String ALGORITHM = "HmacSHA256";
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private HmacUtil() {
    }

    public static byte[] compute(byte[] key, byte[] message) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(message, "message");
        if (key.length == 0) {
            throw new IllegalArgumentException("HMAC key must not be empty");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return mac.doFinal(message);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // HmacSHA256 is a JCA-standard algorithm name guaranteed present on every
            // conforming JVM, and SecretKeySpec never rejects a non-empty byte[] key for
            // it. This branch indicates a broken JVM, not a caller error, so it is
            // wrapped as unchecked rather than added to every caller's signature.
            throw new IllegalStateException("HmacSHA256 is unavailable on this JVM", e);
        }
    }

    public static String hex(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX_DIGITS[v >>> 4];
            out[i * 2 + 1] = HEX_DIGITS[v & 0x0F];
        }
        return new String(out);
    }

    public static byte[] fromHex(String hex) {
        Objects.requireNonNull(hex, "hex");
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("hex string must have an even length: " + hex);
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("not a valid hex string: " + hex);
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
