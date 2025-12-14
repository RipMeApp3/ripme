package com.rarchives.ripme.db;


import com.rarchives.ripme.utils.Utils;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Handles SQLite database connection, initialization, and schema migrations.
 */
public class DatabaseManager {
    private static final Logger logger = LogManager.getLogger(DatabaseManager.class);

    private final Object lock = new Object();
    private static final int MAX_QUERY_RETRIES = 5;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private static final Duration MAINTENANCE_PERIOD = Duration.ofHours(1);

    private static final Set<SQLiteErrorCode> RETRY_ERROR_CODES = Set.of(
            SQLiteErrorCode.SQLITE_BUSY,
            SQLiteErrorCode.SQLITE_LOCKED,
            SQLiteErrorCode.SQLITE_BUSY_RECOVERY,
            SQLiteErrorCode.SQLITE_BUSY_SNAPSHOT,
            SQLiteErrorCode.SQLITE_BUSY_TIMEOUT,
            SQLiteErrorCode.SQLITE_LOCKED_SHAREDCACHE,
            SQLiteErrorCode.SQLITE_LOCKED_VTAB
    );

    private static final Set<String> RETRY_MESSAGES = Set.of(
            // SQLiteConnection#checkOpen()
            "database connection closed",

            //java.sql.SQLException: The database has been closed
            //        at org.sqlite.core.NativeDB.throwex(NativeDB.java:503)
            "The database has been closed"
    );

    // Don't use generic DataSource; need to close the connection pool for WAL cleanup
    //private final HikariDataSource dataSource;

    private final DataSource dataSource;

    private boolean useSerialAccess = false;

    // Even with WAL and HikariCP, still occasionally getting
    // "[SQLITE_BUSY] The database file is locked (database is locked)"
    // Hide parallel access behind a flag to ease migration to a proper database in the future
    private static final boolean wantSerialAccess = Utils.getConfigBoolean("db.serial.access", true);

    // For non-pooled serial access...
    private Connection connection;

    public final String dbUrl;

    /**
     * Create a manager for the given SQLite file path.
     * Example path: "ripme.sqlite", an absolute file path, or ":memory:".<br>
     * Call {@link #initialize()} before anything uses the database.<br>
     * Regular application code should use {@link #withConnection(ConnectionOperation)} to get a connection for statements.
     */
    public DatabaseManager(String dbPath) {
        // Some of this file is dead or in-progress code,
        // but I want to keep it to use for future improvement.
        Objects.requireNonNull(dbPath, "filePath");
        // "jdbc:sqlite:" is more commonly seen, but that shortcut
        // does not support creating named in-memory databases,
        // and "jdbc:sqlite:file:" does
        this.dbUrl = "jdbc:sqlite:file:" + dbPath;

        SQLiteDataSource dataSource = getSQLiteDataSource();
        //if (!"WAL".equals(dataSource.getConfig().toProperties().
        //        getProperty(SQLiteConfig.Pragma.JOURNAL_MODE.pragmaName))) {
        //    needsSerialAccess = true;
        //}

        //HikariConfig hikariConfig = new HikariConfig();
        //hikariConfig.setDataSource(dataSource);
        //hikariConfig.setMaximumPoolSize(6);

        boolean usingSqlite = this.dbUrl.startsWith("jdbc:sqlite:");
        boolean needSerialAccess = dbPath.startsWith(":memory:") || dbPath.matches(".*[?&]mode=memory\\b.*");
        if (wantSerialAccess || (usingSqlite && needSerialAccess)) {
            // Do not allow concurrent connections for in-memory databases
            useSerialAccess = true;
            //hikariConfig.setMaximumPoolSize(1);
        }
        //HikariDataSource hikariDataSource = new HikariDataSource(hikariConfig);
        //this.dataSource = hikariDataSource;

        this.dataSource = dataSource;
        executor.scheduleAtFixedRate(this::refreshConnection, 0, MAINTENANCE_PERIOD.toMinutes(), TimeUnit.MINUTES);
    }

    /**
     * Periodically refresh the connection as a precaution to prevent resource leaks
     */
    private void refreshConnection() {
        synchronized (lock) {
            try {
                // Recreate the connection
                if (connection != null) {
                    logger.debug("Refreshing db connection");
                    connection.close(); // dangerous outside of synchronized(lock)
                    connection = null; // allow the connection to be lazily created by getConnection()
                }
            } catch (SQLException e) {
                logger.debug("db connection not able to be refreshed", e);
                // Do nothing
            }
        }
    }

    private SQLiteDataSource getSQLiteDataSource() {
        SQLiteConfig sqliteConfig = new SQLiteConfig();
        sqliteConfig.setPragma(SQLiteConfig.Pragma.FOREIGN_KEYS, "ON");
        sqliteConfig.setPragma(SQLiteConfig.Pragma.BUSY_TIMEOUT, "5000"); // milliseconds
        sqliteConfig.setPragma(SQLiteConfig.Pragma.JOURNAL_MODE, "WAL");
        sqliteConfig.setPragma(SQLiteConfig.Pragma.SYNCHRONOUS, "NORMAL");
        sqliteConfig.setPragma(SQLiteConfig.Pragma.CACHE_SIZE, "1048576"); // kibibytes; default -2000
        // Note: if initializing the db with gradle flywayMigrate, you need to VACUUM; to apply the new page_size
        //sqliteConfig.setPragma(SQLiteConfig.Pragma.PAGE_SIZE, "16384"); // bytes; default 4096
        sqliteConfig.setPragma(SQLiteConfig.Pragma.TEMP_STORE, "MEMORY");
        sqliteConfig.setPragma(SQLiteConfig.Pragma.RECURSIVE_TRIGGERS, "OFF");
        // Prevent SQLITE_BUSY on concurrent access when a transaction gets upgraded from read to write:
        sqliteConfig.setPragma(SQLiteConfig.Pragma.TRANSACTION_MODE, "IMMEDIATE");
        SQLiteDataSource dataSource = new SQLiteDataSource(sqliteConfig);
        dataSource.setUrl(dbUrl);
        return dataSource;
    }

    public void initialize() {
        Flyway flyway = Flyway.configure()
                // Don't bother pooling for flyway
                .dataSource(getSQLiteDataSource())
                .loggers("log4j2") // some library transitively pulls in slf4j
                .locations("db/migration")
                .load();
        try {
            flyway.migrate();
        } catch (FlywayException e) {
            logger.fatal("Error while trying to migrate database", e);
            throw new RuntimeException(e);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(this::cleanupWalShutdown));
    }

    private void cleanupWalShutdown() {
        logger.debug("Cleaning up sqlite WAL file for shutdown");

        if (dataSource instanceof HikariDataSource) {
            // Close the connection pool so that only one connection will be open for the wal_checkpoint
            ((HikariDataSource) dataSource).close();
            if (((HikariDataSource) dataSource).getHikariPoolMXBean().getActiveConnections() > 0) {
                try {
                    // Hope that any open connections finish in 20ms
                    Thread.sleep(20);
                } catch (InterruptedException ignored) {
                }
            }
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }

        // Use a plain SQLiteDataSource for cleanup, no pool
        SQLiteDataSource dataSource = getSQLiteDataSource();
        boolean fallbackToFull = false;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA wal_checkpoint(TRUNCATE)");
        } catch (SQLException e) {
            fallbackToFull = true;
        }
        if (fallbackToFull) {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA wal_checkpoint(FULL)");
            } catch (SQLException e) {
                logger.info("Not able to clean up the sqlite WAL file: {}", e.getMessage());
            }
        }
    }

    public void cleanupWal() {
        logger.debug("Cleaning up sqlite WAL file");
        try {
            withConnection(conn -> {
                boolean fallbackToFull = false;
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA wal_checkpoint(TRUNCATE)");
                } catch (SQLException e) {
                    fallbackToFull = true;
                }
                if (fallbackToFull) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("PRAGMA wal_checkpoint(FULL)");
                    } catch (SQLException e) {
                        logger.debug("Not able to clean up the sqlite WAL file; not a problem: {}", e.getMessage());
                    }
                }
            });
        } catch (SQLException e) {
            logger.debug("Not able to clean up the sqlite WAL file; not a problem: {}", e.getMessage());
        }
    }

    public Connection getConnection() throws SQLException {
        // The pragma is handled by setEnforceForeignKeys(true) above
        //Connection conn = DriverManager.getConnection(dbUrl);
        //try (Statement s = conn.createStatement()) {
        //    s.execute("PRAGMA foreign_keys = ON");
        //}
        //return conn;

        // Non-pooling:
        if (connection == null || connection.isClosed()) {
            connection = dataSource.getConnection();
        }
        connection.setAutoCommit(true);
        return connection;
    }

    /**
     * SQLite struggles with parallelism in-memory or without WAL.
     * If using WAL, no synchronization needed.
     * If in memory or no WAL, we synchronize at the cost of some performance to avoid errors like<br>
     * {@code [SQLITE_LOCKED_SHAREDCACHE] Contention with a different database connection that shares the cache (database table is locked)}<br>
     * and<br>
     * {@code [SQLITE_BUSY] The database file is locked (database is locked)}<br>
     * <br>
     * Example:
     * <pre>{@code
     * int hundred = db.withConnection(conn -> {
     *     try (Statement s = conn.createStatement()) {
     *         ResultSet rs = s.executeQuery("SELECT 100");
     *         return rs.next() ? rs.getInt(1) : 0;
     *     }
     * });
     * }</pre>
     *
     * @param operation The statements to be synchronized
     * @param <T>       The return type of the operation
     * @return The return value of the operation
     * @throws SQLException When connection was not created
     */
    public <T> T withConnection(ConnectionOperation<T> operation) throws SQLException {
        // Variant for connection pool...
        //if (useSerialAccess) {
        //    synchronized (lock) {
        //        try (Connection conn = getConnection()) {
        //            T applied = operation.apply(conn);
        //            return applied;
        //        }
        //    }
        //} else {
        //    try (Connection conn = getConnection()) {
        //        T applied = operation.apply(conn);
        //        return applied;
        //    }
        //}
        if (useSerialAccess) {
            synchronized (lock) {
                return applySqlOperationWithRetry(operation);
            }
        } else {
            return applySqlOperationWithRetry(operation);
        }
    }

    /**
     * @see #withConnection(ConnectionOperation)
     */
    public void withConnection(ConnectionOperationVoid operation) throws SQLException {
        // Variant for connection pool...
        //if (useSerialAccess) {
        //    synchronized (lock) {
        //        try (Connection conn = getConnection()) {
        //            operation.apply(conn);
        //        }
        //    }
        //} else {
        //    try (Connection conn = getConnection()) {
        //        operation.apply(conn);
        //    }
        //}
        if (useSerialAccess) {
            synchronized (lock) {
                applySqlOperationWithRetryVoid(operation);
            }
        } else {
            applySqlOperationWithRetryVoid(operation);
        }
    }

    private <T> T applySqlOperationWithRetry(ConnectionOperation<T> operation) throws SQLException {
        int tries = 0;
        while (true) {
            try {
                T applied = operation.apply(getConnection());
                return applied;
            } catch (SQLiteException e) {
                boolean retriesRemain = ++tries < MAX_QUERY_RETRIES;
                if (retriesRemain && RETRY_ERROR_CODES.contains(e.getResultCode())) {
                    try {
                        // No exponential backoff for now...
                        Thread.sleep(10);
                    } catch (InterruptedException ex) {
                        // Continue to next try
                    }
                    logger.debug("Retrying sql operation, retry #{}", tries, e);
                } else {
                    throw e;
                }
            } catch (SQLException e) {
                boolean retriesRemain = ++tries < MAX_QUERY_RETRIES;
                if (retriesRemain && RETRY_MESSAGES.contains(e.getMessage())) {
                    try {
                        // No exponential backoff for now...
                        Thread.sleep(10);
                    } catch (InterruptedException ex) {
                        // Continue to next try
                    }
                    logger.debug("Retrying sql operation, retry #{}", tries, e);
                } else {
                    throw e;
                }
            }
        }
    }

    private void applySqlOperationWithRetryVoid(ConnectionOperationVoid operation) throws SQLException {
        int tries = 0;
        while (true) {
            try {
                operation.apply(getConnection());
                return;
            } catch (SQLiteException e) {
                boolean retriesRemain = ++tries < MAX_QUERY_RETRIES;
                if (retriesRemain && RETRY_ERROR_CODES.contains(e.getResultCode())) {
                    try {
                        // No exponential backoff for now...
                        Thread.sleep(10);
                    } catch (InterruptedException ex) {
                        // Continue to next try
                    }
                    logger.debug("Retrying sql operation, retry #{}", tries, e);
                } else {
                    throw e;
                }
            } catch (SQLException e) {
                boolean retriesRemain = ++tries < MAX_QUERY_RETRIES;
                if (retriesRemain && RETRY_MESSAGES.contains(e.getMessage())) {
                    try {
                        // No exponential backoff for now...
                        Thread.sleep(10);
                    } catch (InterruptedException ex) {
                        // Continue to next try
                    }
                    logger.debug("Retrying sql operation, retry #{}", tries, e);
                } else {
                    throw e;
                }
            }
        }
    }
}
