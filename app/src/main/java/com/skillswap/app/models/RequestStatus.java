package com.skillswap.app.models;

import com.skillswap.app.exception.InvalidStatusTransitionException;

import java.util.EnumSet;
import java.util.Set;

/**
 * The lifecycle of a {@link SwapRequest}, modeled as a small finite state machine
 * instead of a free-form String. Before this existed, "status" was one of four
 * {@code public static final String} constants on {@code SwapRequest} with nothing
 * stopping a typo, an unrelated string, or an illegal jump (e.g. REJECTED -&gt;
 * ACCEPTED) from being written.
 *
 * <pre>
 *   PENDING --accept--&gt; ACCEPTED --complete--&gt; COMPLETED
 *      \--reject--&gt; REJECTED
 * </pre>
 * REJECTED and COMPLETED are terminal: nothing can leave them.
 */
public enum RequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    COMPLETED;

    private static final Set<RequestStatus> TERMINAL = EnumSet.of(REJECTED, COMPLETED);

    /** Whether a request in this status may legally move to {@code target}. */
    public boolean canTransitionTo(RequestStatus target) {
        if (target == null) {
            return false;
        }
        switch (this) {
            case PENDING:
                return target == ACCEPTED || target == REJECTED;
            case ACCEPTED:
                return target == COMPLETED;
            default:
                return false; // REJECTED, COMPLETED are terminal
        }
    }

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /**
     * Same check as {@link #canTransitionTo}, but throws instead of returning false.
     * Intended for call sites that have already decided to attempt the transition and
     * want a clear failure rather than a silently-ignored no-op.
     */
    public void requireTransitionTo(RequestStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(this, target);
        }
    }
}
