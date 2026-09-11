package com.skillswap.app.firebase;

/**
 * Single source of truth for every Firebase Realtime Database path segment used
 * across the app. Every class that touches the database MUST reference these
 * constants instead of hardcoding string literals, so paths never drift apart.
 *
 * Database layout:
 *   users/{uid}
 *   swapRequests/{requestId}
 *   messages/{conversationId}/{messageId}   conversationId = sorted(uidA_uidB)
 *   sessions/{sessionId}
 *   reviews/{reviewId}
 *   favorites/{uid}/{targetUid} : true
 */
public final class DatabasePaths {

    private DatabasePaths() {
        // no instances
    }

    public static final String USERS = "users";
    public static final String SWAP_REQUESTS = "swapRequests";
    public static final String MESSAGES = "messages";
    public static final String SESSIONS = "sessions";
    public static final String REVIEWS = "reviews";
    public static final String FAVORITES = "favorites";

    /**
     * Builds the deterministic conversation id for a 1-1 chat between two uids.
     * Sorting alphabetically means both participants compute the same id
     * regardless of who initiated the conversation.
     */
    public static String conversationId(String uidA, String uidB) {
        if (uidA == null || uidB == null) return null;
        return uidA.compareTo(uidB) < 0 ? uidA + "_" + uidB : uidB + "_" + uidA;
    }
}
