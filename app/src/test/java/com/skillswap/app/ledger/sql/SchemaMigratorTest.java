package com.skillswap.app.ledger.sql;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SchemaMigratorTest {

    private static DataSource freshDatabase(String name) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        return dataSource;
    }

    @Test
    public void migratingAFreshDatabaseCreatesAllExpectedTables() throws Exception {
        DataSource dataSource = freshDatabase("migrator1");
        new SchemaMigrator(dataSource).migrate();

        assertTrue(tableExists(dataSource, "ACCOUNTS"));
        assertTrue(tableExists(dataSource, "LEDGER_ENTRIES"));
        assertTrue(tableExists(dataSource, "SCHEMA_MIGRATIONS"));
    }

    @Test
    public void everyMigrationFileIsRecordedAsApplied() throws Exception {
        DataSource dataSource = freshDatabase("migrator2");
        new SchemaMigrator(dataSource).migrate();

        List<String> appliedVersions = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT version FROM schema_migrations ORDER BY version")) {
            while (resultSet.next()) {
                appliedVersions.add(resultSet.getString("version"));
            }
        }
        assertEquals(List.of("V1", "V2", "V3"), appliedVersions);
    }

    @Test
    public void runningMigrateTwiceIsANoOpTheSecondTime() throws Exception {
        DataSource dataSource = freshDatabase("migrator3");
        SchemaMigrator migrator = new SchemaMigrator(dataSource);

        migrator.migrate(); // applies V1, V2, V3
        migrator.migrate(); // must not try to re-run any of them (would fail: tables already exist)

        int migrationCount = countRows(dataSource, "schema_migrations");
        assertEquals(3, migrationCount);
    }

    private static boolean tableExists(DataSource dataSource, String tableName) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ResultSet tables = connection.getMetaData().getTables(null, null, tableName, null);
            return tables.next();
        }
    }

    private static int countRows(DataSource dataSource, String table) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) AS c FROM " + table)) {
            resultSet.next();
            return resultSet.getInt("c");
        }
    }
}
