package com.skillswap.app.models;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class MessageTest {

    @Test
    public void messageIdMayBeNullBeforeFirebaseAssignsAKey() {
        Message message = new Message(null, "alice", "bob", "hi there");
        assertNull(message.getMessageId());
    }

    @Test
    public void blankTextIsRejected() {
        try {
            new Message(null, "alice", "bob", "   ");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void aUserCannotMessageThemselves() {
        try {
            new Message(null, "alice", "alice", "hi");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void twoUnsentMessagesAreNotEqualEvenWithSameContent() {
        Message a = new Message(null, "alice", "bob", "hi");
        Message b = new Message(null, "alice", "bob", "hi");
        assertNotEquals(a, b); // neither has a messageId yet -> not "the same message"
    }

    @Test
    public void sentMessagesAreEqualByMessageId() {
        Message a = new Message(null, "alice", "bob", "hi");
        a.setMessageId("m1");
        Message b = new Message(null, "carol", "dave", "different text");
        b.setMessageId("m1");
        assertEquals(a, b);
    }
}
