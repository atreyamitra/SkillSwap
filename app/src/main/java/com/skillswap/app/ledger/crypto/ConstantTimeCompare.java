package com.skillswap.app.ledger.crypto;

import java.security.MessageDigest;

/**
 * Byte-array equality that does not leak timing information about where the first
 * differing byte is.
 *
 * <p><b>Why this matters here, and how much:</b> the ledger's integrity check compares
 * a recomputed HMAC against a stored one (see {@code LedgerIntegrityVerifier}). Using
 * {@code String.equals} or {@code Arrays.equals} for that comparison exits as soon as a
 * mismatched byte is found, so the comparison takes measurably less time for a "more
 * wrong" guess than a "nearly right" one — the textbook timing side-channel used to
 * brute-force a MAC byte-by-byte in constant-time-oracle attacks (e.g. against a
 * network-facing signature check).
 *
 * <p>In this codebase specifically, that attack is largely theoretical: integrity
 * verification runs against an in-memory ledger the attacker would need local access
 * to already tamper with, not a network endpoint an attacker can repeatedly probe with
 * timing measurements. This class exists anyway, for two reasons stated plainly rather
 * than oversold: (1) it is correct, free, and exactly as easy to write as the unsafe
 * version, so there is no reason not to; (2) if this comparison were ever reused for a
 * network-facing check (verifying an inbound webhook's HMAC signature, for instance —
 * a realistic future use of the same primitive), that IS a genuine timing-attack
 * surface, and the safe version would already be in place.
 */
public final class ConstantTimeCompare {

    private ConstantTimeCompare() {
    }

    /**
     * @return true iff {@code a} and {@code b} contain the same bytes. Delegates to
     * {@link MessageDigest#isEqual(byte[], byte[])}, which the JDK documents as
     * resistant to timing attacks specifically for the equal-length case — the only
     * case that occurs here, since both arguments are always 32-byte HMAC-SHA256
     * outputs.
     */
    public static boolean equals(byte[] a, byte[] b) {
        if (a == null || b == null) {
            return a == b;
        }
        return MessageDigest.isEqual(a, b);
    }
}
