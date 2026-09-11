package com.skillswap.app.models;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SwapRequestTest {

    @Test
    public void newRequestStartsPending() {
        SwapRequest request = new SwapRequest("r1", "alice", "bob", "Alice", "Bob", "Java", "Guitar");
        assertEquals(RequestStatus.PENDING, request.getStatus());
    }

    @Test
    public void setStatusRoundTripsThroughTheEnum() {
        SwapRequest request = new SwapRequest("r1", "alice", "bob", "Alice", "Bob", "Java", "Guitar");
        request.setStatus(RequestStatus.ACCEPTED);
        assertEquals(RequestStatus.ACCEPTED, request.getStatus());
    }

    @Test
    public void setStatusRejectsNull() {
        SwapRequest request = new SwapRequest("r1", "alice", "bob", "Alice", "Bob", "Java", "Guitar");
        try {
            request.setStatus(null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
            // expected
        }
    }

    @Test
    public void cannotSendARequestToOneself() {
        try {
            new SwapRequest("r1", "alice", "alice", "Alice", "Alice", "Java", "Guitar");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void equalityIsByRequestIdOnly() {
        SwapRequest a = new SwapRequest("r1", "alice", "bob", "Alice", "Bob", "Java", "Guitar");
        SwapRequest b = new SwapRequest("r1", "carol", "dave", "Carol", "Dave", "Painting", "Cooking");
        SwapRequest c = new SwapRequest("r2", "alice", "bob", "Alice", "Bob", "Java", "Guitar");

        assertEquals(a, b); // same requestId, different other fields -> still "the same request"
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    public void aRequestIsNotEqualToNullOrAnotherType() {
        SwapRequest a = new SwapRequest("r1", "alice", "bob", "Alice", "Bob", "Java", "Guitar");
        assertNotEquals(a, null);
        assertNotEquals(a, "r1");
    }

    @Test
    public void newRequestHasARecentTimestamp() {
        long before = System.currentTimeMillis();
        SwapRequest request = new SwapRequest("r1", "alice", "bob", "Alice", "Bob", "Java", "Guitar");
        long after = System.currentTimeMillis();
        assertTrue(request.getTimestamp() >= before && request.getTimestamp() <= after);
    }
}
