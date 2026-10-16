package com.fintrack.app.config.liquibase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

/**
 * Owns an isolated schema used by a Liquibase migration fixture on one explicitly test-owned JDBC session.
 *
 * <p>The scope deliberately requires auto-commit. A fixture that changes {@code search_path} while an ordinary
 * application transaction is in progress could otherwise leave an aborted transaction behind or attempt to roll back
 * work it does not own. With auto-commit enabled each fixture statement is isolated. If Liquibase changes that mode,
 * close rolls back only the fixture-owned transaction before restoring the exact pre-fixture session setting.
 */
final class MigrationTestSchemaScope implements AutoCloseable {

    private static final Pattern SAFE_SCHEMA_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final Connection connection;
    private final String schema;
    private final String quotedSchema;
    private final String originalSearchPath;
    private boolean closed;

    private MigrationTestSchemaScope(Connection connection, String schema, String originalSearchPath) {
        this.connection = connection;
        this.schema = schema;
        this.quotedSchema = quoteIdentifier(schema);
        this.originalSearchPath = originalSearchPath;
    }

    static MigrationTestSchemaScope open(Connection connection, String schema) throws SQLException {
        if (!connection.getAutoCommit()) {
            throw new IllegalStateException("Migration fixture connections must use auto-commit before changing search_path");
        }

        MigrationTestSchemaScope scope = new MigrationTestSchemaScope(connection, schema, currentSearchPath(connection));
        try {
            scope.createAndSelectSchema();
            return scope;
        } catch (SQLException failure) {
            try {
                scope.close();
            } catch (SQLException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    String schema() {
        return schema;
    }

    @Override
    public void close() throws SQLException {
        if (closed) {
            return;
        }
        closed = true;

        SQLException failure = null;
        try {
            closeFixtureTransactionIfNecessary();
        } catch (SQLException transactionFailure) {
            failure = transactionFailure;
        }

        try {
            setSearchPath(connection, originalSearchPath);
        } catch (SQLException restoreFailure) {
            failure = append(failure, restoreFailure);
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("drop schema if exists " + quotedSchema + " cascade");
        } catch (SQLException dropFailure) {
            failure = append(failure, dropFailure);
        }

        if (failure != null) {
            throw failure;
        }
    }

    private void createAndSelectSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("create schema " + quotedSchema);
        }
        setSearchPath(connection, quotedSchema);
    }

    private void closeFixtureTransactionIfNecessary() throws SQLException {
        if (!connection.getAutoCommit()) {
            connection.rollback();
            connection.setAutoCommit(true);
        }
    }

    static String currentSearchPath(Connection connection) throws SQLException {
        try (
            Statement statement = connection.createStatement();
            var resultSet = statement.executeQuery("select current_setting('search_path')")
        ) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private static void setSearchPath(Connection connection, String searchPath) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select set_config('search_path', ?, false)")) {
            statement.setString(1, searchPath);
            statement.execute();
        }
    }

    private static SQLException append(SQLException primary, SQLException additional) {
        if (primary == null) {
            return additional;
        }
        primary.addSuppressed(additional);
        return primary;
    }

    private static String quoteIdentifier(String identifier) {
        if (!SAFE_SCHEMA_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Unsafe schema identifier: " + identifier);
        }
        return '"' + identifier + '"';
    }
}
