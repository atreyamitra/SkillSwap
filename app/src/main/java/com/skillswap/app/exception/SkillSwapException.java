package com.skillswap.app.exception;

/**
 * Base type for business-rule violations in the SkillSwap domain — as opposed to
 * programmer errors (bad arguments, nulls), which use the standard JDK unchecked
 * exceptions ({@link IllegalArgumentException}, {@link NullPointerException}) instead.
 *
 * This is deliberately a small, flat hierarchy: a demo app has exactly two business
 * rules worth their own exception type (see the two subclasses). It is unchecked
 * because every caller in this codebase is a UI layer that can only react by showing
 * the message to the user — there's no recovery logic that would benefit from the
 * compiler forcing a catch.
 */
public abstract class SkillSwapException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    protected SkillSwapException(String message) {
        super(message);
    }
}
