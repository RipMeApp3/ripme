package com.rarchives.ripme.db;

import org.junit.jupiter.api.Test;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseManagerTest {

    @Test
    void initialize() throws SQLException {
        DatabaseManager db = new DatabaseManager(":memory:?cache=shared");
        // Keep the in-memory db alive with a persistent connection unused by the test
        try (Connection ignored = db.getConnection()) {
            db.initialize();
            long migrationCount = 0;
            try (Connection connection = db.getConnection();
                 Statement stmt = connection.createStatement()) {
                ResultSet resultSet = stmt.executeQuery("SELECT count(*) FROM flyway_schema_history");
                migrationCount = resultSet.getLong(1);
            }
            //create table flyway_schema_history
            //(
            //    installed_rank INT not null
            //        primary key,
            //    version VARCHAR(50),
            //    description VARCHAR(200) not null,
            //    type VARCHAR(20) not null,
            //    script VARCHAR(1000) not null,
            //    checksum INT,
            //    installed_by VARCHAR(100) not null,
            //    installed_on TEXT default (strftime('%Y-%m-%d %H:%M:%f','now')) not null,
            //    execution_time INT not null,
            //    success BOOLEAN not null
            //);

            // Manually update the test for each additional migration from scratch.
            // Worth manually updating to catch accidentally committed schema changes.
            assertEquals(2, migrationCount, "Migration count should be 2");
        }
    }
}

