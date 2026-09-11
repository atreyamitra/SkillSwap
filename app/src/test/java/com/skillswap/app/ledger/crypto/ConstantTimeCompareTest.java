package com.skillswap.app.ledger.crypto;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConstantTimeCompareTest {

    @Test
    public void identicalArraysAreEqual() {
        byte[] a = "same-bytes".getBytes(StandardCharsets.UTF_8);
        byte[] b = "same-bytes".getBytes(StandardCharsets.UTF_8);
        assertTrue(ConstantTimeCompare.equals(a, b));
    }

    @Test
    public void differingInTheLastByteIsNotEqual() {
        byte[] a = "aaaaaaaaaaaaaaaaX".getBytes(StandardCharsets.UTF_8);
        byte[] b = "aaaaaaaaaaaaaaaaY".getBytes(StandardCharsets.UTF_8);
        assertFalse(ConstantTimeCompare.equals(a, b));
    }

    @Test
    public void differingInTheFirstByteIsNotEqual() {
        byte[] a = "Xaaaaaaaaaaaaaaaa".getBytes(StandardCharsets.UTF_8);
        byte[] b = "Yaaaaaaaaaaaaaaaa".getBytes(StandardCharsets.UTF_8);
        assertFalse(ConstantTimeCompare.equals(a, b));
    }

    @Test
    public void differentLengthsAreNotEqual() {
        assertFalse(ConstantTimeCompare.equals(new byte[]{1, 2, 3}, new byte[]{1, 2}));
    }

    @Test
    public void bothNullIsEqual() {
        assertTrue(ConstantTimeCompare.equals(null, null));
    }

    @Test
    public void oneNullIsNotEqual() {
        assertFalse(ConstantTimeCompare.equals(null, new byte[]{1}));
        assertFalse(ConstantTimeCompare.equals(new byte[]{1}, null));
    }

    @Test
    public void emptyArraysAreEqual() {
        assertTrue(ConstantTimeCompare.equals(new byte[0], new byte[0]));
    }
}
