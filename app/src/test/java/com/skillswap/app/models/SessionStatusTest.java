package com.skillswap.app.models;

import com.skillswap.app.exception.InvalidStatusTransitionException;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SessionStatusTest {

    @Test
    public void scheduledCanMoveToCompletedOrCancelled() {
        assertTrue(SessionStatus.SCHEDULED.canTransitionTo(SessionStatus.COMPLETED));
        assertTrue(SessionStatus.SCHEDULED.canTransitionTo(SessionStatus.CANCELLED));
    }

    @Test
    public void completedAndCancelledAreTerminal() {
        assertTrue(SessionStatus.COMPLETED.isTerminal());
        assertTrue(SessionStatus.CANCELLED.isTerminal());
        assertFalse(SessionStatus.COMPLETED.canTransitionTo(SessionStatus.SCHEDULED));
        assertFalse(SessionStatus.CANCELLED.canTransitionTo(SessionStatus.COMPLETED));
    }

    @Test
    public void requireTransitionToThrowsOnIllegalMove() {
        try {
            SessionStatus.COMPLETED.requireTransitionTo(SessionStatus.SCHEDULED);
            fail("expected InvalidStatusTransitionException");
        } catch (InvalidStatusTransitionException expected) {
            // expected
        }
    }
}
