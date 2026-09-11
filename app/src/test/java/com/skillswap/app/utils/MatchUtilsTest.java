package com.skillswap.app.utils;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link MatchUtils}. This is the natural first class to unit test in the
 * project because it is a pure function with no Android framework dependency (see
 * BUILD_NOTES.md, "What testing exists?").
 */
public class MatchUtilsTest {

    @Test
    public void fullMutualOverlap_scoresHundred() {
        List<String> myTeach = Arrays.asList("Java", "DSA");
        List<String> myWant = Arrays.asList("Guitar");
        List<String> theirTeach = Arrays.asList("Guitar");
        List<String> theirWant = Arrays.asList("Java", "DSA");

        MatchUtils.MatchResult result = MatchUtils.computeMatch(myTeach, myWant, theirTeach, theirWant);

        assertEquals(100, result.score);
        assertEquals(1, result.theyTeachYouWant.size());
        assertEquals(2, result.youTeachTheyWant.size());
    }

    @Test
    public void noOverlap_scoresZero() {
        List<String> myTeach = Arrays.asList("Java");
        List<String> myWant = Arrays.asList("Guitar");
        List<String> theirTeach = Arrays.asList("Cooking");
        List<String> theirWant = Arrays.asList("Painting");

        MatchUtils.MatchResult result = MatchUtils.computeMatch(myTeach, myWant, theirTeach, theirWant);

        assertEquals(0, result.score);
        assertTrue(result.theyTeachYouWant.isEmpty());
        assertTrue(result.youTeachTheyWant.isEmpty());
    }

    @Test
    public void oneWayOverlap_scoresHalf() {
        // Other teaches something I want, but I don't teach anything they want.
        List<String> myTeach = Arrays.asList("Java");
        List<String> myWant = Arrays.asList("Guitar");
        List<String> theirTeach = Arrays.asList("Guitar");
        List<String> theirWant = Arrays.asList("Cooking");

        MatchUtils.MatchResult result = MatchUtils.computeMatch(myTeach, myWant, theirTeach, theirWant);

        assertEquals(50, result.score);
    }

    @Test
    public void partialWantListCoverage_isScaledProportionally() {
        // Other teaches 1 of my 2 wanted skills -> half of the 50-point half, i.e. 25.
        List<String> myTeach = Collections.emptyList();
        List<String> myWant = Arrays.asList("Guitar", "Photography");
        List<String> theirTeach = Arrays.asList("Guitar");
        List<String> theirWant = Collections.emptyList();

        MatchUtils.MatchResult result = MatchUtils.computeMatch(myTeach, myWant, theirTeach, theirWant);

        assertEquals(25, result.score);
    }

    @Test
    public void matchIsCaseInsensitiveAndTrimsWhitespace() {
        List<String> myTeach = Collections.emptyList();
        List<String> myWant = Arrays.asList("guitar ");
        List<String> theirTeach = Arrays.asList(" Guitar");
        List<String> theirWant = Collections.emptyList();

        MatchUtils.MatchResult result = MatchUtils.computeMatch(myTeach, myWant, theirTeach, theirWant);

        assertEquals(50, result.score);
        assertEquals(1, result.theyTeachYouWant.size());
    }

    @Test
    public void emptyWantList_doesNotDivideByZero() {
        List<String> myTeach = Arrays.asList("Java");
        List<String> myWant = Collections.emptyList();
        List<String> theirTeach = Arrays.asList("Guitar");
        List<String> theirWant = Arrays.asList("Java");

        MatchUtils.MatchResult result = MatchUtils.computeMatch(myTeach, myWant, theirTeach, theirWant);

        assertEquals(50, result.score);
    }

    @Test
    public void nullLists_areHandledSafely() {
        MatchUtils.MatchResult result = MatchUtils.computeMatch(null, null, null, null);

        assertEquals(0, result.score);
        assertTrue(result.theyTeachYouWant.isEmpty());
        assertTrue(result.youTeachTheyWant.isEmpty());
    }

    @Test
    public void duplicateSkillsInWantList_doNotInflateScoreAboveOverlapSize() {
        // theirTeach has one distinct skill; myWant lists it twice. The intersection helper
        // walks theirTeach outer / myWant inner, so each theirTeach entry is counted at most
        // once regardless of duplicates on the "want" side.
        List<String> myTeach = Collections.emptyList();
        List<String> myWant = Arrays.asList("Guitar", "Guitar");
        List<String> theirTeach = Arrays.asList("Guitar");
        List<String> theirWant = Collections.emptyList();

        MatchUtils.MatchResult result = MatchUtils.computeMatch(myTeach, myWant, theirTeach, theirWant);

        assertEquals(1, result.theyTeachYouWant.size());
    }
}
