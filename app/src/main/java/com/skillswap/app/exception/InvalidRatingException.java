package com.skillswap.app.exception;

/**
 * Thrown when a {@code Review} is constructed with a star rating outside the valid
 * 1-5 range. The UI (a row of 5 star buttons) already makes this practically
 * unreachable, but the constructor enforces it too rather than trusting every future
 * caller to remember the constraint.
 */
public class InvalidRatingException extends SkillSwapException {

    private static final long serialVersionUID = 1L;

    public InvalidRatingException(int rating) {
        super("Rating must be between 1 and 5, got: " + rating);
    }
}
