package com.fintrack.app.config.liquibase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fintrack.app.IntegrationTest;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class MigrationTestSchemaScopeIT {

    @Autowired
    private DataSource dataSource;

    @Test
    void restoresTheExactSearchPathAndDropsFixtureSchemasBeforeThePooledSessionIsReused() throws Exception {
        try (HikariDataSource isolatedPool = isolatedSingleConnectionPool()) {
            String originalSearchPath;
            int backendPid;
            String successfulSchema = schemaName("migration_scope_success_");
            String failedSchema = schemaName("migration_scope_failure_");

            try (Connection fixtureConnection = isolatedPool.getConnection()) {
                backendPid = backendPid(fixtureConnection);
                originalSearchPath = MigrationTestSchemaScope.currentSearchPath(fixtureConnection);

                runSuccessfulFixture(fixtureConnection, successfulSchema);
                assertRestoredFixtureState(fixtureConnection, originalSearchPath, successfulSchema);

                assertThatThrownBy(() -> runFailingFixture(fixtureConnection, failedSchema))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("intentional migration fixture failure");
                assertRestoredFixtureState(fixtureConnection, originalSearchPath, failedSchema);
            }

            try (Connection reusedConnection = isolatedPool.getConnection()) {
                assertThat(backendPid(reusedConnection)).isEqualTo(backendPid);
                assertRestoredFixtureState(reusedConnection, originalSearchPath, successfulSchema);
                assertRestoredFixtureState(reusedConnection, originalSearchPath, failedSchema);
            }
        }
    }

    private void runSuccessfulFixture(Connection connection, String schema) throws SQLException {
        try (
            MigrationTestSchemaScope ignored = MigrationTestSchemaScope.open(connection, schema);
            Statement statement = connection.createStatement()
        ) {
            statement.execute("create table migration_fixture_success (id bigint primary key)");
        }
    }

    private void runFailingFixture(Connection connection, String schema) throws SQLException {
        try (
            MigrationTestSchemaScope ignored = MigrationTestSchemaScope.open(connection, schema);
            Statement statement = connection.createStatement()
        ) {
            statement.execute("create table migration_fixture_failure (id bigint primary key)");
            throw new IllegalStateException("intentional migration fixture failure");
        }
    }

    private void assertRestoredFixtureState(Connection connection, String originalSearchPath, String schema) throws SQLException {
        assertThat(connection.getAutoCommit()).isTrue();
        assertThat(MigrationTestSchemaScope.currentSearchPath(connection)).isEqualTo(originalSearchPath);
        assertThat(schemaExists(connection, schema)).isFalse();
        assertApplicationSchemaIsUsable(connection);
    }

    private boolean schemaExists(Connection connection, String schema) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select to_regnamespace(?)");) {
            statement.setString(1, schema);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString(1) != null;
            }
        }
    }

    private void assertApplicationSchemaIsUsable(Connection connection) throws SQLException {
        try (
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery("select count(*) from jhi_user")
        ) {
            assertThat(resultSet.next()).isTrue();
        }
    }

    private int backendPid(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("select pg_backend_pid()")) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1);
        }
    }

    private HikariDataSource isolatedSingleConnectionPool() {
        assertThat(dataSource).isInstanceOf(HikariDataSource.class);
        HikariDataSource applicationPool = (HikariDataSource) dataSource;
        HikariConfig configuration = new HikariConfig();
        configuration.setJdbcUrl(applicationPool.getJdbcUrl());
        configuration.setUsername(applicationPool.getUsername());
        configuration.setPassword(applicationPool.getPassword());
        if (applicationPool.getDriverClassName() != null) {
            configuration.setDriverClassName(applicationPool.getDriverClassName());
        }
        configuration.setAutoCommit(true);
        configuration.setMaximumPoolSize(1);
        configuration.setMinimumIdle(1);
        configuration.setPoolName("migration-schema-scope-regression");
        return new HikariDataSource(configuration);
    }

    private String schemaName(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
