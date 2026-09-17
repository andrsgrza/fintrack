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

/**
 * Runs the provenance changelog against a deliberately pre-TI-CONFIG-0A schema with historical candidate data.
 */
@IntegrationTest
class TransactionCandidateClassificationProvenanceMigrationIT {

    @Autowired
    private DataSource dataSource;

    @Test
    void migrationConservativelyBackfillsProvenanceAndPreservesRelationIntegrity() throws Exception {
        String schema = "tc_provenance_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            statement.execute("create schema " + schema);
            statement.execute("set search_path to " + schema);
            createPreProvenanceSchema(statement);
            insertHistoricalData(statement);

            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            database.setDefaultSchemaName(schema);
            new Liquibase(
                "config/liquibase/changelog/20260915160000_transaction_candidate_classification_provenance.xml",
                new ClassLoaderResourceAccessor(),
                database
            ).update(new Contexts(), new LabelExpression());
            connection.commit();
            connection.setAutoCommit(true);

            assertThat(column(statement, "select category_source from transaction_candidate where id = 1")).isEqualTo("MANUAL");
            assertThat(column(statement, "select category_source from transaction_candidate where id = 2")).isEqualTo("AUTOMATIC");
            assertThat(column(statement, "select category_source from transaction_candidate where id = 3")).isNull();
            assertThat(
                column(statement, "select source from rel_transaction_candidate__tags where transaction_candidate_id = 1 and tags_id = 11")
            ).isEqualTo("MANUAL");
            assertThat(
                column(statement, "select source from rel_transaction_candidate__tags where transaction_candidate_id = 2 and tags_id = 12")
            ).isEqualTo("AUTOMATIC");
            assertThat(column(statement, "select count(*) from rel_transaction_candidate__tags where id is null")).isEqualTo("0");

            assertConstraintRejects(
                statement,
                "insert into rel_transaction_candidate__tags (id, transaction_candidate_id, tags_id, source) values (9001, 1, 11, 'MANUAL')",
                "23505"
            );
            assertConstraintRejects(
                statement,
                "insert into rel_transaction_candidate__tags (id, transaction_candidate_id, tags_id, source) values (9002, 1, 12, null)",
                "23502"
            );
            assertConstraintRejects(
                statement,
                "insert into rel_transaction_candidate__tags (id, transaction_candidate_id, tags_id, source) values (9003, 9999, 11, 'AUTOMATIC')",
                "23503"
            );
        } finally {
            try (Connection cleanupConnection = dataSource.getConnection(); Statement cleanup = cleanupConnection.createStatement()) {
                cleanup.execute("drop schema if exists " + schema + " cascade");
            }
        }
    }

    private void createPreProvenanceSchema(Statement statement) throws SQLException {
        statement.execute("create sequence sequence_generator start with 100");
        statement.execute(
            "create table transaction_candidate (id bigint primary key, category_id bigint, classification_review_status varchar(255) not null)"
        );
        statement.execute("create table tag (id bigint primary key)");
        statement.execute(
            "create table rel_transaction_candidate__tags (transaction_candidate_id bigint not null references transaction_candidate(id), tags_id bigint not null references tag(id), primary key (transaction_candidate_id, tags_id))"
        );
    }

    private void insertHistoricalData(Statement statement) throws SQLException {
        statement.execute(
            "insert into transaction_candidate (id, category_id, classification_review_status) values (1, 101, 'USER_SELECTED'), (2, 102, 'SUGGESTED'), (3, null, 'NOT_EVALUATED')"
        );
        statement.execute("insert into tag (id) values (11), (12)");
        statement.execute(
            "insert into rel_transaction_candidate__tags (transaction_candidate_id, tags_id) values (1, 11), (1, 12), (2, 12)"
        );
    }

    private String column(Statement statement, String sql) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
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
}
