package com.skillswap.app.firebase;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Unit tests for {@link DatabasePaths#conversationId}, the chat "room" key derivation. */
public class DatabasePathsTest {

    @Test
    public void sortsUidsAlphabeticallyRegardlessOfArgumentOrder() {
        assertEquals("uidA_uidB", DatabasePaths.conversationId("uidA", "uidB"));
        assertEquals("uidA_uidB", DatabasePaths.conversationId("uidB", "uidA"));
    }

    @Test
    public void isDeterministicForTheSamePair() {
        String first = DatabasePaths.conversationId("alice", "bob");
        String second = DatabasePaths.conversationId("bob", "alice");
        assertEquals(first, second);
    }

    @Test
    public void nullUid_returnsNullInsteadOfThrowing() {
        assertNull(DatabasePaths.conversationId(null, "bob"));
        assertNull(DatabasePaths.conversationId("alice", null));
        assertNull(DatabasePaths.conversationId(null, null));
    }
}
