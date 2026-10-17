package com.fintrack.app.config.liquibase;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.IntegrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Runs the new persistence changeset against a minimal PostgreSQL schema and characterizes its physical constraints. */
@IntegrationTest
class TransactionBatchPersistenceMigrationIT {

    private static final String CHANGELOG = "config/liquibase/changelog/20261015120000_transaction_batch_persistence.xml";

    @Autowired
    private DataSource dataSource;

    @Test
    void createsOnlyCoherentOpenOrPublishedRootsAndCleansTemporaryChildrenWithTheirBatch() throws Exception {
        String schema = "transaction_batch_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            try (
                MigrationTestSchemaScope schemaScope = MigrationTestSchemaScope.open(connection, schema);
                Statement statement = connection.createStatement()
            ) {
                createPreBatchSchema(statement);
                runMigration(connection, schemaScope.schema());
                // Liquibase uses transactional DDL and may leave the connection in manual-commit mode.
                // Each intentional constraint violation below must be isolated from the next assertion.
                connection.setAutoCommit(true);
                insertReferences(statement);

                statement.execute(
                    """
                    insert into transaction_batch
                        (id, version, status, next_row_position, auto_apply_descriptions, auto_apply_categories, auto_apply_tags,
                         protect_manual_changes, created_at, updated_at, user_id, account_id)
                    values (100, 0, 'OPEN', 2, false, false, false, true, current_timestamp, current_timestamp, 1, 10)
                    """
                );
                assertConstraintRejects(
                    statement,
                    "insert into transaction_batch (id, version, status, next_row_position, created_at, updated_at, user_id) values (101, 0, 'OPEN', 1, current_timestamp, current_timestamp, 1)",
                    "23514"
                );
                statement.execute(
                    """
                    insert into transaction_batch
                        (id, version, status, created_at, updated_at, published_at, publication_operation_key,
                         publication_request_digest, published_transaction_count, receipt_expires_at, user_id)
                    values (102, 0, 'PUBLISHED', current_timestamp, current_timestamp, current_timestamp, 'publish-1',
                            'digest', 0, current_timestamp + interval '1 day', 1)
                    """
                );
                assertConstraintRejects(
                    statement,
                    "insert into transaction_batch (id, version, status, next_row_position, created_at, updated_at, user_id, account_id) values (103, 0, 'PUBLISHED', 1, current_timestamp, current_timestamp, 1, 10)",
                    "23514"
                );

                statement.execute(
                    "insert into transaction_candidate (id, version, category_manual_clear, transaction_batch_id, row_position) values (200, 0, false, 100, 1)"
                );
                assertConstraintRejects(
                    statement,
                    "insert into transaction_candidate (id, version, category_manual_clear, transaction_batch_id, row_position) values (201, 0, false, 100, 1)",
                    "23505"
                );
                assertConstraintRejects(
                    statement,
                    "insert into transaction_candidate (id, version, category_manual_clear, row_position) values (202, 0, false, 1)",
                    "23514"
                );
                statement.execute(
                    "insert into transaction_candidate_tag_suppression (id, transaction_candidate_id, tag_id) values (300, 200, 20)"
                );
                statement.execute(
                    "insert into transaction_candidate_input_issue (id, field, reason_code, submitted_value, transaction_candidate_id) values (400, 'CURRENCY', 'CURRENCY_MISMATCH', 'USD', 200)"
                );

                statement.execute("delete from transaction_batch where id = 100");
                assertThat(count(statement, "select count(*) from transaction_candidate where id = 200")).isZero();
                assertThat(count(statement, "select count(*) from transaction_candidate_tag_suppression where id = 300")).isZero();
                assertThat(count(statement, "select count(*) from transaction_candidate_input_issue where id = 400")).isZero();
                assertThat(count(statement, "select count(*) from transaction_batch where id = 102")).isOne();
            }
        }
    }

    private void createPreBatchSchema(Statement statement) throws SQLException {
        statement.execute("create table jhi_user (id bigint primary key)");
        statement.execute("create table financial_account (id bigint primary key)");
        statement.execute("create table tag (id bigint primary key)");
        statement.execute("create table transaction_candidate (id bigint primary key)");
    }

    private void runMigration(Connection connection, String schema) throws Exception {
        Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
        database.setDefaultSchemaName(schema);
        Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database);
        // The normal master changelog supplies this portable type property; isolated migration coverage supplies the same value.
        liquibase.setChangeLogParameter("datetimeType", "timestamp with time zone");
        liquibase.update(new Contexts(), new LabelExpression());
    }

    private void insertReferences(Statement statement) throws SQLException {
        statement.execute("insert into jhi_user (id) values (1)");
        statement.execute("insert into financial_account (id) values (10)");
        statement.execute("insert into tag (id) values (20)");
    }

    private void assertConstraintRejects(Statement statement, String sql, String expectedSqlState) {
        try {
            statement.execute(sql);
        } catch (SQLException expected) {
            assertThat(expected.getSQLState()).isEqualTo(expectedSqlState);
            return;
        }
        throw new AssertionError("Expected database constraint to reject: " + sql);
    }

    private long count(Statement statement, String sql) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }
}
