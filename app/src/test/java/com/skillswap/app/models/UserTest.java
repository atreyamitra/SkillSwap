package com.skillswap.app.models;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

public class UserTest {

    @Test
    public void noArgConstructorStartsWithEmptySkillLists() {
        User user = new User();
        assertEquals(Collections.emptyList(), user.getSkillsTeach());
        assertEquals(Collections.emptyList(), user.getSkillsWant());
    }

    @Test
    public void getSkillsTeachReturnsAnUnmodifiableView() {
        User user = new User("u1", "Alice", "alice@example.com");
        user.setSkillsTeach(Arrays.asList("Java", "Guitar"));
        try {
            user.getSkillsTeach().add("Cooking");
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    @Test
    public void setSkillsTeachCopiesRatherThanAliasesTheInputList() {
        List<String> source = new java.util.ArrayList<>(Arrays.asList("Java"));
        User user = new User("u1", "Alice", "alice@example.com");
        user.setSkillsTeach(source);
        source.add("Guitar"); // mutate the caller's own list after handing it off

        assertEquals(1, user.getSkillsTeach().size()); // user's copy is unaffected
    }

    @Test
    public void equalityIsByUidOnly() {
        User a = new User("u1", "Alice", "alice@example.com");
        User b = new User("u1", "Alice Renamed", "alice2@example.com");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void differentUidsAreNotEqual() {
        User a = new User("u1", "Alice", "alice@example.com");
        User b = new User("u2", "Alice", "alice@example.com");
        assertNotEquals(a, b);
    }
}
