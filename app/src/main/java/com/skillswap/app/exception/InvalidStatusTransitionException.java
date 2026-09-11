package com.skillswap.app.exception;

/**
 * Thrown when code attempts to move a {@code SwapRequest} or {@code Session} into a
 * status its current status cannot legally reach — e.g. accepting a request that was
 * already rejected. Callers that always check {@code canTransitionTo(...)} first should
 * never see this in practice; it exists as the last line of defense against a bug (or a
 * race between two devices acting on the same request) rather than as routine control flow.
 */
public class InvalidStatusTransitionException extends SkillSwapException {

    private static final long serialVersionUID = 1L;

    public InvalidStatusTransitionException(Object from, Object to) {
        super("Cannot transition from " + from + " to " + to);
    }
}
