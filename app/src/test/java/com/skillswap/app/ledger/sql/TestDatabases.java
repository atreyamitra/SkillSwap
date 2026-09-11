package com.skillswap.app.ledger.sql;

import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test-only helper: a fresh, migrated, uniquely-named in-memory H2 database per call,
 * so tests never share state with each other even when run in the same JVM.
 *
 * <p><b>Why H2, not real PostgreSQL or Testcontainers:</b> this environment has no
 * running Docker daemon (Testcontainers needs one) and no network path to a real
 * Postgres server. H2 is a pure-JVM embedded database with no external process
 * required, which is why it's used here — the same honest tradeoff this project has
 * made before when a preferred tool wasn't reachable (see {@code BUILD_NOTES.md} §1
 * for the Android SDK/AGP equivalent). The schema itself (see
 * {@code app/src/main/resources/db/migration/}) is written in portable, standard SQL
 * — no H2-specific syntax — and {@code MODE=PostgreSQL} is enabled below specifically
 * to catch any accidental non-Postgres-compatible SQL as early as possible. See
 * {@code docs/DATABASE_DESIGN.md} for the full rationale.
 */
final class TestDatabases {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private TestDatabases() {
    }

    static DataSource freshMigratedDatabase() {
        String name = "ledgertest" + COUNTER.incrementAndGet();
        JdbcDataSource dataSource = new JdbcDataSource();
        // DB_CLOSE_DELAY=-1 keeps the in-memory database alive for the life of the
        // JVM (or until explicitly dropped) rather than being destroyed the instant
        // the first connection closes -- required so multiple connections/threads in
        // a concurrency test can share the same in-memory database.
        dataSource.setURL("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        new SchemaMigrator(dataSource).migrate();
        return dataSource;
    }
}
