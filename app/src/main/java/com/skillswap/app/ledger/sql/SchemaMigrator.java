package com.skillswap.app.ledger.sql;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A minimal, dependency-free migration runner: applies the numbered {@code .sql}
 * files under {@code db/migration/} on the classpath, in version order, recording
 * each applied version in a {@code schema_migrations} table so a migration is never
 * re-applied. This is deliberately a small hand-written mechanism rather than a
 * Flyway/Liquibase dependency — the naming convention ({@code V<version>__<description>.sql})
 * mirrors Flyway's for familiarity, but pulling in a real migration framework for
 * three DDL files would be exactly the "fake complexity" this pass was asked to
 * avoid; this class is simple enough to read in full in under a minute.
 *
 * <p>Each migration file is applied inside its own transaction: either every
 * statement in that file commits, or none of it does and the migration is not
 * recorded as applied (so a fixed, corrected version of the same file could be
 * retried on a future run — though in practice this project's migrations are meant
 * to be immutable once applied, exactly like any other migration tool's convention).
 */
public final class SchemaMigrator {

    private static final Pattern FILE_NAME_PATTERN = Pattern.compile("^(V\\d+)__.*\\.sql$");
    private static final String MIGRATIONS_RESOURCE_DIR = "db/migration/";
    /** Listed explicitly rather than scanned from the classpath: JAR/classpath
     *  resource listing is not portable across all runtimes, and this project has a
     *  small, fixed set of migrations reviewed by hand, not a build step that
     *  generates this list — see docs/DATABASE_DESIGN.md "Tradeoffs". */
    private static final List<String> MIGRATION_FILES = List.of(
            "V1__create_accounts_table.sql",
            "V2__create_ledger_entries_table.sql",
            "V3__create_ledger_entries_indexes.sql"
    );

    private final DataSource dataSource;

    public SchemaMigrator(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    /** Applies every migration not yet recorded as applied, in version order. */
    public void migrate() {
        try (Connection connection = dataSource.getConnection()) {
            ensureMigrationsTable(connection);
            Set<String> applied = alreadyApplied(connection);

            List<String> pending = new ArrayList<>();
            for (String fileName : MIGRATION_FILES) {
                String version = versionOf(fileName);
                if (!applied.contains(version)) {
                    pending.add(fileName);
                }
            }
            pending.sort(Comparator.comparing(SchemaMigrator::versionOf, SchemaMigrator::compareVersions));

            for (String fileName : pending) {
                applyMigration(connection, fileName);
            }
        } catch (SQLException e) {
            throw new LedgerPersistenceException("Schema migration failed", e);
        }
    }

    private void ensureMigrationsTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS schema_migrations (" +
                            "version VARCHAR(50) NOT NULL, " +
                            "applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                            "CONSTRAINT pk_schema_migrations PRIMARY KEY (version))");
        }
    }

    private Set<String> alreadyApplied(Connection connection) throws SQLException {
        Set<String> applied = new TreeSet<>();
        try (Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT version FROM schema_migrations")) {
            while (resultSet.next()) {
                applied.add(resultSet.getString("version"));
            }
        }
        return applied;
    }

    private void applyMigration(Connection connection, String fileName) throws SQLException {
        String sql = readResource(MIGRATIONS_RESOURCE_DIR + fileName);
        String version = versionOf(fileName);

        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            for (String statementSql : splitStatements(sql)) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(statementSql);
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO schema_migrations (version) VALUES (?)")) {
                insert.setString(1, version);
                insert.executeUpdate();
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw new LedgerPersistenceException("Migration " + fileName + " failed and was rolled back", e);
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    private static String versionOf(String fileName) {
        Matcher matcher = FILE_NAME_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            throw new IllegalStateException("Migration file name does not match V<n>__description.sql: " + fileName);
        }
        return matcher.group(1);
    }

    private static int compareVersions(String a, String b) {
        int numA = Integer.parseInt(a.substring(1));
        int numB = Integer.parseInt(b.substring(1));
        return Integer.compare(numA, numB);
    }

    /** Splits a migration file's text into individual statements on semicolons that
     *  terminate a line, which is sufficient for this project's simple DDL and avoids
     *  pulling in a real SQL parser for three files, each of which is written with
     *  one statement per semicolon and no string literal containing one. */
    private static List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        for (String candidate : sql.split(";")) {
            String trimmed = candidate.trim();
            if (!trimmed.isEmpty()) {
                statements.add(trimmed);
            }
        }
        return statements;
    }

    private static String readResource(String path) {
        ClassLoader classLoader = SchemaMigrator.class.getClassLoader();
        try (InputStream in = classLoader.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Migration resource not found on classpath: " + path);
            }
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().startsWith("--")) {
                        content.append(line).append('\n');
                    }
                }
            }
            return content.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read migration resource: " + path, e);
        }
    }
}
