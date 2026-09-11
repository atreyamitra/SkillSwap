package com.skillswap.app.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Computes an explainable 0-100% match score between the current user and another
 * user, based purely on skill-list overlap. Intended to be simple enough to explain
 * in a viva without needing any external ML library.
 *
 * Scoring rule (out of 100):
 *   - Up to 50 points: how much of what the OTHER user teaches is something the
 *     CURRENT user wants to learn. Scaled by overlap size relative to the
 *     current user's "want" list, so 100% overlap gives the full 50 points.
 *   - Up to 50 points: how much of what the CURRENT user teaches is something the
 *     OTHER user wants to learn (the reverse direction, so the swap is mutual).
 *
 * Both halves are computed independently so the algorithm rewards a two-way trade
 * (teach + learn) more than a one-way overlap, which better reflects a genuine
 * "swap".
 */
public final class MatchUtils {

    private MatchUtils() {
    }

    public static class MatchResult {
        public final int score; // 0-100
        public final List<String> theyTeachYouWant;
        public final List<String> youTeachTheyWant;

        MatchResult(int score, List<String> theyTeachYouWant, List<String> youTeachTheyWant) {
            this.score = score;
            this.theyTeachYouWant = theyTeachYouWant;
            this.youTeachTheyWant = youTeachTheyWant;
        }
    }

    public static MatchResult computeMatch(List<String> myTeach, List<String> myWant,
                                            List<String> theirTeach, List<String> theirWant) {
        List<String> theyTeachYouWant = intersectionCaseInsensitive(theirTeach, myWant);
        List<String> youTeachTheyWant = intersectionCaseInsensitive(myTeach, theirWant);

        double half1 = 0;
        if (myWant != null && !myWant.isEmpty()) {
            half1 = 50.0 * theyTeachYouWant.size() / myWant.size();
        }
        double half2 = 0;
        if (myTeach != null && !myTeach.isEmpty()) {
            half2 = 50.0 * youTeachTheyWant.size() / myTeach.size();
        }

        int score = (int) Math.round(Math.min(50, half1) + Math.min(50, half2));
        return new MatchResult(score, theyTeachYouWant, youTeachTheyWant);
    }

    private static List<String> intersectionCaseInsensitive(List<String> a, List<String> b) {
        List<String> result = new ArrayList<>();
        if (a == null || b == null) return result;
        for (String x : a) {
            if (x == null) continue;
            for (String y : b) {
                if (y == null) continue;
                if (x.trim().toLowerCase(Locale.ROOT).equals(y.trim().toLowerCase(Locale.ROOT))) {
                    result.add(x);
                    break;
                }
            }
        }
        return result;
    }
}
