package com.skillswap.app.models;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SessionTest {

    @Test
    public void newSessionStartsScheduled() {
        Session session = new Session("s1", "r1", "alice", "bob", "12 Sep 2026", "17:30", 1_800_000_000_000L);
        assertEquals(SessionStatus.SCHEDULED, session.getStatus());
    }

    @Test
    public void setStatusRoundTripsThroughTheEnum() {
        Session session = new Session("s1", "r1", "alice", "bob", "12 Sep 2026", "17:30", 1_800_000_000_000L);
        session.setStatus(SessionStatus.COMPLETED);
        assertEquals(SessionStatus.COMPLETED, session.getStatus());
    }

    @Test
    public void bothParticipantsMustBeDifferentUsers() {
        try {
            new Session("s1", "r1", "alice", "alice", "12 Sep 2026", "17:30", 1_800_000_000_000L);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void equalityIsBySessionIdOnly() {
        Session a = new Session("s1", "r1", "alice", "bob", "12 Sep 2026", "17:30", 1_800_000_000_000L);
        Session b = new Session("s1", "rX", "carol", "dave", "1 Jan 2030", "09:00", 1_900_000_000_000L);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
