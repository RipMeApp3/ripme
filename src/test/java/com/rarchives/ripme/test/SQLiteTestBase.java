package com.rarchives.ripme.test;

import com.rarchives.ripme.db.DatabaseManager;
import com.rarchives.ripme.db.DbInitializeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.sqlite.SQLiteDataSource;

import javax.xml.crypto.Data;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Provides setup and teardown with {@link WithDb} and {@link WithInMemoryDb}.
 */
public class SQLiteTestBase {

    // Persistent connection to keep the in-memory database alive.
    // JUnit creates a new instance for each test method invocation,
    // so the connection is scoped to the test case.
    private Connection connection;
    protected DatabaseManager db;

    @BeforeEach
    public void setup(TestInfo testInfo) throws SQLException, ReflectiveOperationException, DbInitializeException {
        Method testMethod = testInfo.getTestMethod().get();
        Method getSQLiteDataSource = DatabaseManager.class.getDeclaredMethod("getSQLiteDataSource");
        getSQLiteDataSource.setAccessible(true);
        boolean isWithInMemoryDb = testMethod.isAnnotationPresent(WithInMemoryDb.class);
        boolean isWithDb = testMethod.isAnnotationPresent(WithDb.class);
        if (isWithInMemoryDb && isWithDb) {
            throw new IllegalArgumentException("Only one @WithInMemoryDb and @WithDb annotation can be used");
        } else if (isWithInMemoryDb) {
            db = new DatabaseManager("db_" + testMethod.hashCode() + "?mode=memory&cache=shared");
            // "db_test?mode=memory&cache=shared"

            // Don't use the HikariCP pool (explanation below)
            //connection = db.getConnection();

            // Get the data source without HikariCP so that this idle connection doesn't consume the pool.
            SQLiteDataSource sqliteDataSource = (SQLiteDataSource) getSQLiteDataSource.invoke(db);
            connection = sqliteDataSource.getConnection();

            db.initialize();
        } else if (isWithDb) {
            WithDb withDb = testMethod.getAnnotation(WithDb.class);
            db = new DatabaseManager(withDb.value());
            //connection = db.getConnection();

            SQLiteDataSource sqliteDataSource = (SQLiteDataSource) getSQLiteDataSource.invoke(db);
            connection = sqliteDataSource.getConnection();

            db.initialize();
        } else {
            // TODO remove fallback?
            // I don't like creating a default db here, but I might prefer this
            // over putting @WithInMemoryDb on each test case
            db = new DatabaseManager("db_" + testMethod.hashCode() + "?mode=memory&cache=shared");
            //connection = db.getConnection();

            SQLiteDataSource sqliteDataSource = (SQLiteDataSource) getSQLiteDataSource.invoke(db);
            connection = sqliteDataSource.getConnection();

            db.initialize();
        }
    }
    @AfterEach
    public void teardown() throws SQLException {
        if (connection != null) {
            connection.close();
            db.cleanupWal();
        }
    }
}

