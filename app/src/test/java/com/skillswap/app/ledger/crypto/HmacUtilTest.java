package com.skillswap.app.ledger.crypto;

import org.junit.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

public class HmacUtilTest {

    @Test
    public void agreesWithADirectJdkMacComputation() throws NoSuchAlgorithmException, InvalidKeyException {
        // Independent second implementation of the same primitive, so this test isn't
        // just checking that HmacUtil agrees with itself.
        byte[] key = "correct-horse-battery-staple".getBytes(StandardCharsets.UTF_8);
        byte[] message = "transfer:alice->bob:10.00".getBytes(StandardCharsets.UTF_8);

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        byte[] expected = mac.doFinal(message);

        assertArrayEquals(expected, HmacUtil.compute(key, message));
    }

    @Test
    public void isDeterministic() {
        byte[] key = "k".getBytes(StandardCharsets.UTF_8);
        byte[] message = "m".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(HmacUtil.compute(key, message), HmacUtil.compute(key, message));
    }

    @Test
    public void differentKeysProduceDifferentMacsForTheSameMessage() {
        byte[] message = "m".getBytes(StandardCharsets.UTF_8);
        byte[] macA = HmacUtil.compute("key-a".getBytes(StandardCharsets.UTF_8), message);
        byte[] macB = HmacUtil.compute("key-b".getBytes(StandardCharsets.UTF_8), message);
        assertNotEquals(HmacUtil.hex(macA), HmacUtil.hex(macB));
    }

    @Test
    public void differentMessagesProduceDifferentMacsForTheSameKey() {
        byte[] key = "k".getBytes(StandardCharsets.UTF_8);
        byte[] macA = HmacUtil.compute(key, "message-a".getBytes(StandardCharsets.UTF_8));
        byte[] macB = HmacUtil.compute(key, "message-b".getBytes(StandardCharsets.UTF_8));
        assertNotEquals(HmacUtil.hex(macA), HmacUtil.hex(macB));
    }

    @Test
    public void outputIsAlways32BytesForSha256() {
        byte[] mac = HmacUtil.compute("k".getBytes(StandardCharsets.UTF_8), new byte[0]);
        assertEquals(32, mac.length);
    }

    @Test
    public void emptyKeyIsRejected() {
        try {
            HmacUtil.compute(new byte[0], "m".getBytes(StandardCharsets.UTF_8));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void hexRoundTripsThroughFromHex() {
        byte[] original = HmacUtil.compute("k".getBytes(StandardCharsets.UTF_8), "m".getBytes(StandardCharsets.UTF_8));
        String hex = HmacUtil.hex(original);
        assertEquals(64, hex.length()); // 32 bytes -> 64 hex chars
        assertArrayEquals(original, HmacUtil.fromHex(hex));
    }

    @Test
    public void fromHexRejectsOddLength() {
        try {
            HmacUtil.fromHex("abc");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void fromHexRejectsNonHexCharacters() {
        try {
            HmacUtil.fromHex("zz");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
