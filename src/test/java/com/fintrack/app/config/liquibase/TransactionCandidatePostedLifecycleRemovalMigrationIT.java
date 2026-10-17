package com.fintrack.app.config.liquibase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fintrack.app.IntegrationTest;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
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
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Verifies that the POSTED lifecycle removal never drops historical candidate links silently.
 */
@IntegrationTest
class TransactionCandidatePostedLifecycleRemovalMigrationIT {

    private static final String CHANGELOG = "config/liquibase/changelog/20261008143000_remove_transaction_candidate_posted_lifecycle.xml";

    @Autowired
    private DataSource dataSource;

    @Test
    void removesObsoleteColumnsWhenNoPostedCandidatesRemain() throws Exception {
        String schema = newSchema("tc_posted_lifecycle_clean_");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            try (
                MigrationTestSchemaScope schemaScope = MigrationTestSchemaScope.open(connection, schema);
                Statement statement = connection.createStatement()
            ) {
                createLegacyCandidateTables(statement);
                statement.execute("insert into financial_transaction (id) values (100)");
                statement.execute(
                    "insert into transaction_candidate (id, status, financial_transaction_id, posted_at) values (1, 'READY_TO_POST', null, null)"
                );

                runMigration(connection, schemaScope.schema());

                assertThat(columnExists(connection, schemaScope.schema(), "transaction_candidate", "financial_transaction_id")).isFalse();
                assertThat(columnExists(connection, schemaScope.schema(), "transaction_candidate", "posted_at")).isFalse();
            }
        }
    }

    @Test
    void haltsBeforeAnyDestructiveStepWhenPostedCandidatesRemain() throws Exception {
        String schema = newSchema("tc_posted_lifecycle_blocked_");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            try (
                MigrationTestSchemaScope schemaScope = MigrationTestSchemaScope.open(connection, schema);
                Statement statement = connection.createStatement()
            ) {
                createLegacyCandidateTables(statement);
                statement.execute("insert into financial_transaction (id) values (100)");
                statement.execute(
                    "insert into transaction_candidate (id, status, financial_transaction_id, posted_at) values (1, 'POSTED', 100, current_timestamp)"
                );

                assertThatThrownBy(() -> runMigration(connection, schemaScope.schema()))
                    .isInstanceOf(LiquibaseException.class)
                    .hasMessageContaining("POSTED candidates remain");

                assertThat(columnExists(connection, schemaScope.schema(), "transaction_candidate", "financial_transaction_id")).isTrue();
                assertThat(columnExists(connection, schemaScope.schema(), "transaction_candidate", "posted_at")).isTrue();
            }
        }
    }

    private String newSchema(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    private void createLegacyCandidateTables(Statement statement) throws SQLException {
        statement.execute("create table financial_transaction (id bigint primary key)");
        statement.execute(
            "create table transaction_candidate (id bigint primary key, status varchar(255) not null, financial_transaction_id bigint, posted_at timestamp)"
        );
        statement.execute(
            "alter table transaction_candidate add constraint fk_transaction_candidate__financial_transaction_id foreign key (financial_transaction_id) references financial_transaction(id)"
        );
        statement.execute(
            "alter table transaction_candidate add constraint ux_transaction_candidate__financial_transaction_id unique (financial_transaction_id)"
        );
    }

    private void runMigration(Connection connection, String schema) throws LiquibaseException {
        Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
        database.setDefaultSchemaName(schema);
        new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database).update(new Contexts(), new LabelExpression());
    }

    private boolean columnExists(Connection connection, String schema, String table, String column) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (var columns = metadata.getColumns(null, schema, table, column)) {
            return columns.next();
        }
    }
}
