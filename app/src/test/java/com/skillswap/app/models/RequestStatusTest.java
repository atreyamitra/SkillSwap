package com.skillswap.app.models;

import com.skillswap.app.exception.InvalidStatusTransitionException;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RequestStatusTest {

    @Test
    public void pendingCanMoveToAcceptedOrRejected() {
        assertTrue(RequestStatus.PENDING.canTransitionTo(RequestStatus.ACCEPTED));
        assertTrue(RequestStatus.PENDING.canTransitionTo(RequestStatus.REJECTED));
        assertFalse(RequestStatus.PENDING.canTransitionTo(RequestStatus.COMPLETED));
        assertFalse(RequestStatus.PENDING.canTransitionTo(RequestStatus.PENDING));
    }

    @Test
    public void acceptedCanOnlyMoveToCompleted() {
        assertTrue(RequestStatus.ACCEPTED.canTransitionTo(RequestStatus.COMPLETED));
        assertFalse(RequestStatus.ACCEPTED.canTransitionTo(RequestStatus.PENDING));
        assertFalse(RequestStatus.ACCEPTED.canTransitionTo(RequestStatus.REJECTED));
    }

    @Test
    public void rejectedAndCompletedAreTerminal() {
        assertTrue(RequestStatus.REJECTED.isTerminal());
        assertTrue(RequestStatus.COMPLETED.isTerminal());
        assertFalse(RequestStatus.REJECTED.canTransitionTo(RequestStatus.PENDING));
        assertFalse(RequestStatus.COMPLETED.canTransitionTo(RequestStatus.ACCEPTED));
    }

    @Test
    public void pendingAndAcceptedAreNotTerminal() {
        assertFalse(RequestStatus.PENDING.isTerminal());
        assertFalse(RequestStatus.ACCEPTED.isTerminal());
    }

    @Test
    public void canTransitionToNullIsFalse() {
        assertFalse(RequestStatus.PENDING.canTransitionTo(null));
    }

    @Test
    public void requireTransitionToThrowsOnIllegalMove() {
        try {
            RequestStatus.REJECTED.requireTransitionTo(RequestStatus.ACCEPTED);
            fail("expected InvalidStatusTransitionException");
        } catch (InvalidStatusTransitionException expected) {
            assertTrue(expected.getMessage().contains("REJECTED"));
            assertTrue(expected.getMessage().contains("ACCEPTED"));
        }
    }

    @Test
    public void requireTransitionToSucceedsSilentlyOnLegalMove() {
        RequestStatus.PENDING.requireTransitionTo(RequestStatus.ACCEPTED); // must not throw
    }
}
