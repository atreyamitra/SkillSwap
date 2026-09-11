package com.skillswap.app.models;

import com.skillswap.app.exception.InvalidRatingException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ReviewTest {

    @Test
    public void ratingWithinRangeIsAccepted() {
        Review review = new Review("rv1", "alice", "Alice", "bob", "swap1", 5, "Great swap!");
        assertEquals(5, review.getRating());
    }

    @Test
    public void ratingBelowMinimumIsRejected() {
        try {
            new Review("rv1", "alice", "Alice", "bob", "swap1", 0, "");
            fail("expected InvalidRatingException");
        } catch (InvalidRatingException expected) {
            // expected
        }
    }

    @Test
    public void ratingAboveMaximumIsRejected() {
        try {
            new Review("rv1", "alice", "Alice", "bob", "swap1", 6, "");
            fail("expected InvalidRatingException");
        } catch (InvalidRatingException expected) {
            // expected
        }
    }

    @Test
    public void aUserCannotReviewThemselves() {
        try {
            new Review("rv1", "alice", "Alice", "alice", "swap1", 5, "");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void nullCommentIsNormalizedToEmptyString() {
        Review review = new Review("rv1", "alice", "Alice", "bob", "swap1", 5, null);
        assertEquals("", review.getComment());
    }

    @Test
    public void equalityIsByReviewIdOnly() {
        Review a = new Review("rv1", "alice", "Alice", "bob", "swap1", 5, "nice");
        Review b = new Review("rv1", "carol", "Carol", "dave", "swap2", 1, "meh");
        assertEquals(a, b);
    }
}
