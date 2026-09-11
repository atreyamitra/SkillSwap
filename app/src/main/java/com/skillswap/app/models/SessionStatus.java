package com.skillswap.app.models;

import com.skillswap.app.exception.InvalidStatusTransitionException;

/**
 * The lifecycle of a {@link Session}. Mirrors {@link RequestStatus}'s role: replaces
 * three free-form String constants on {@code Session} with a checked state machine.
 *
 * <pre>
 *   SCHEDULED --complete--&gt; COMPLETED
 *   SCHEDULED --cancel--&gt;   CANCELLED
 * </pre>
 * COMPLETED and CANCELLED are terminal. Only {@code SCHEDULED -&gt; COMPLETED} is wired
 * up to a UI action today ({@code SessionRepository.markCompleted}); CANCELLED is
 * modeled because the domain has the concept (a scheduled session can fall through),
 * even though no screen currently offers a "cancel session" button — see TODO.md.
 */
public enum SessionStatus {
    SCHEDULED,
    COMPLETED,
    CANCELLED;

    public boolean canTransitionTo(SessionStatus target) {
        return this == SCHEDULED && (target == COMPLETED || target == CANCELLED);
    }

    public boolean isTerminal() {
        return this != SCHEDULED;
    }

    public void requireTransitionTo(SessionStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(this, target);
        }
    }
}
