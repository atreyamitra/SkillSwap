package com.skillswap.app.ledger;

import com.skillswap.app.ledger.exception.InvalidTransactionException;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TransactionRequestTest {

    // ---- malformed input ----

    @Test
    public void nullIdempotencyKeyIsRejected() {
        assertThrowsInvalid(() -> new TransactionRequest(null, "alice", "bob", amt("1.00"), ""));
    }

    @Test
    public void blankIdempotencyKeyIsRejected() {
        assertThrowsInvalid(() -> new TransactionRequest("   ", "alice", "bob", amt("1.00"), ""));
    }

    @Test
    public void nullPayerIsRejected() {
        assertThrowsInvalid(() -> new TransactionRequest("k1", null, "bob", amt("1.00"), ""));
    }

    @Test
    public void nullPayeeIsRejected() {
        assertThrowsInvalid(() -> new TransactionRequest("k1", "alice", null, amt("1.00"), ""));
    }

    @Test
    public void payerAndPayeeMustDiffer() {
        assertThrowsInvalid(() -> new TransactionRequest("k1", "alice", "alice", amt("1.00"), ""));
    }

    @Test
    public void nullAmountIsRejected() {
        assertThrowsInvalid(() -> new TransactionRequest("k1", "alice", "bob", null, ""));
    }

    @Test
    public void nullDescriptionIsNormalizedToEmptyString() {
        TransactionRequest request = new TransactionRequest("k1", "alice", "bob", amt("1.00"), null);
        assertEquals("", request.getDescription());
    }

    // ---- boundary monetary values ----

    @Test
    public void exactlyMinimumAmountIsAccepted() {
        TransactionRequest request = new TransactionRequest("k1", "alice", "bob", TransactionRequest.MIN_AMOUNT, "");
        assertEquals(TransactionRequest.MIN_AMOUNT, request.getAmount());
    }

    @Test
    public void exactlyMaximumAmountIsAccepted() {
        TransactionRequest request = new TransactionRequest("k1", "alice", "bob", TransactionRequest.MAX_AMOUNT, "");
        assertEquals(TransactionRequest.MAX_AMOUNT, request.getAmount());
    }

    @Test
    public void belowMinimumIsRejected() {
        BigDecimal justBelowMin = TransactionRequest.MIN_AMOUNT.subtract(amt("0.01")); // 0.00
        assertThrowsInvalid(() -> new TransactionRequest("k1", "alice", "bob", justBelowMin, ""));
    }

    @Test
    public void aboveMaximumIsRejected() {
        BigDecimal justAboveMax = TransactionRequest.MAX_AMOUNT.add(amt("0.01"));
        assertThrowsInvalid(() -> new TransactionRequest("k1", "alice", "bob", justAboveMax, ""));
    }

    @Test
    public void zeroIsRejected() {
        assertThrowsInvalid(() -> new TransactionRequest("k1", "alice", "bob", BigDecimal.ZERO, ""));
    }

    @Test
    public void negativeAmountIsRejected() {
        assertThrowsInvalid(() -> new TransactionRequest("k1", "alice", "bob", amt("-5.00"), ""));
    }

    @Test
    public void moreThanTwoDecimalPlacesIsRejected() {
        assertThrowsInvalid(() -> new TransactionRequest("k1", "alice", "bob", amt("1.001"), ""));
    }

    @Test
    public void fewerThanTwoDecimalPlacesIsNormalizedNotRejected() {
        TransactionRequest wholeNumber = new TransactionRequest("k1", "alice", "bob", amt("5"), "");
        assertEquals(amt("5.00"), wholeNumber.getAmount());

        TransactionRequest oneDecimal = new TransactionRequest("k2", "alice", "bob", amt("5.5"), "");
        assertEquals(amt("5.50"), oneDecimal.getAmount());
    }

    // ---- the BigDecimal equals-vs-compareTo trap, resolved by normalization ----

    @Test
    public void requestsWithDifferentlyScaledButNumericallyEqualAmountsAreConsideredTheSameRequest() {
        // Without normalization, new BigDecimal("5.0").equals(new BigDecimal("5.00")) is
        // FALSE (BigDecimal.equals is scale-sensitive) even though they're the same amount.
        // TransactionRequest normalizes both to scale 2 in the constructor, so plain
        // .equals() on the resulting requests is safe and correct.
        TransactionRequest a = new TransactionRequest("same-key", "alice", "bob", amt("5.0"), "lunch");
        TransactionRequest b = new TransactionRequest("same-key", "alice", "bob", amt("5.00"), "lunch");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void requestsDifferingOnlyInAmountAreNotEqual() {
        TransactionRequest a = new TransactionRequest("k1", "alice", "bob", amt("5.00"), "");
        TransactionRequest b = new TransactionRequest("k1", "alice", "bob", amt("6.00"), "");
        org.junit.Assert.assertNotEquals(a, b);
    }

    private static BigDecimal amt(String value) {
        return new BigDecimal(value);
    }

    private interface Thrower {
        void run();
    }

    private static void assertThrowsInvalid(Thrower thrower) {
        try {
            thrower.run();
            fail("expected InvalidTransactionException");
        } catch (InvalidTransactionException expected) {
            // expected
        }
    }
}
