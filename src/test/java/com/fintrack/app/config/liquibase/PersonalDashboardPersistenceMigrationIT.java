package com.fintrack.app.config.liquibase;

import static org.assertj.core.api.Assertions.assertThat;

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
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Verifies that the clean Analytics dashboard schema replaces only the explicitly retired dashboard tables. */
@IntegrationTest
class PersonalDashboardPersistenceMigrationIT {

    private static final String CHANGELOG = "config/liquibase/changelog/20261104100000_personal_dashboard_persistence.xml";

    @Autowired
    private DataSource dataSource;

    @Test
    void replacesTheRetiredDashboardSchemaWithoutTouchingTheOwningUserTable() throws Exception {
        String schema = "personal_dashboard_v2_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            try (
                MigrationTestSchemaScope schemaScope = MigrationTestSchemaScope.open(connection, schema);
                Statement statement = connection.createStatement()
            ) {
                createRetiredDashboardSchema(statement);

                runMigration(connection, schemaScope.schema());

                assertThat(tableExists(connection, schemaScope.schema(), "personal_dashboard_home")).isFalse();
                assertThat(tableExists(connection, schemaScope.schema(), "personal_dashboard_operation")).isFalse();
                assertThat(columnExists(connection, schemaScope.schema(), "personal_dashboard", "default_scope_json")).isFalse();
                assertThat(columnExists(connection, schemaScope.schema(), "personal_dashboard", "default_context_json")).isTrue();
                assertThat(columnExists(connection, schemaScope.schema(), "personal_dashboard_widget", "query_ast_json")).isFalse();
                assertThat(columnExists(connection, schemaScope.schema(), "personal_dashboard_widget", "query_definition_json")).isTrue();
                assertThat(tableExists(connection, schemaScope.schema(), "personal_dashboard_request")).isTrue();
                assertThat(tableExists(connection, schemaScope.schema(), "jhi_user")).isTrue();
            }
        }
    }

    private void createRetiredDashboardSchema(Statement statement) throws SQLException {
        statement.execute("create table jhi_user (id bigint primary key)");
        statement.execute(
            "create table personal_dashboard (id bigint primary key, default_scope_json text not null, user_id bigint not null)"
        );
        statement.execute(
            "create table personal_dashboard_widget (id bigint primary key, query_ast_json text not null, dashboard_id bigint not null references personal_dashboard(id))"
        );
        statement.execute(
            "create table personal_dashboard_home (id bigint primary key, default_dashboard_id bigint references personal_dashboard(id), user_id bigint not null)"
        );
        statement.execute(
            "create table personal_dashboard_operation (id bigint primary key, dashboard_id bigint references personal_dashboard(id), user_id bigint not null)"
        );
    }

    private void runMigration(Connection connection, String schema) throws Exception {
        Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
        database.setDefaultSchemaName(schema);
        Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database);
        liquibase.setChangeLogParameter("datetimeType", "timestamp with time zone");
        liquibase.update(new Contexts(), new LabelExpression());
    }

    private boolean tableExists(Connection connection, String schema, String table) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (var tables = metadata.getTables(null, schema, table, new String[] { "TABLE" })) {
            return tables.next();
        }
    }

    private boolean columnExists(Connection connection, String schema, String table, String column) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (var columns = metadata.getColumns(null, schema, table, column)) {
            return columns.next();
        }
    }
}
