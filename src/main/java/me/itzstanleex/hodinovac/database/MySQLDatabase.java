package me.itzstanleex.hodinovac.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;
import me.itzstanleex.hodinovac.Hodinovac;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * MySQL database handler for Hodinovac plugin.
 *
 * Manages database connections using HikariCP connection pooling,
 * handles table creation, and provides async methods for all database operations.
 * All database operations run asynchronously to prevent blocking the main server thread.
 *
 * @author ItzStanleyX
 * @version 1.0.0
 */
public class MySQLDatabase {

    private final Hodinovac plugin;

    @Getter
    private HikariDataSource dataSource;

    private static final String CREATE_TABLE_QUERY = """
        CREATE TABLE IF NOT EXISTS hodinovac_playtime (
            uuid VARCHAR(36) PRIMARY KEY,
            player_name VARCHAR(16) NOT NULL,
            total_playtime BIGINT NOT NULL DEFAULT 0,
            last_login BIGINT NOT NULL DEFAULT 0,
            last_logout BIGINT NOT NULL DEFAULT 0,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
        """;

    private static final String INSERT_OR_UPDATE_QUERY = """
        INSERT INTO hodinovac_playtime (uuid, player_name, total_playtime, last_login, last_logout)
        VALUES (?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
        player_name = VALUES(player_name),
        total_playtime = VALUES(total_playtime),
        last_login = VALUES(last_login),
        last_logout = VALUES(last_logout),
        updated_at = CURRENT_TIMESTAMP;
        """;

    private static final String SELECT_PLAYTIME_QUERY = """
        SELECT total_playtime FROM hodinovac_playtime WHERE uuid = ?;
        """;

    private static final String SELECT_ALL_DATA_QUERY = """
        SELECT uuid, player_name, total_playtime, last_login, last_logout 
        FROM hodinovac_playtime WHERE uuid = ?;
        """;

    private static final String UPDATE_PLAYTIME_QUERY = """
        UPDATE hodinovac_playtime SET total_playtime = ?, updated_at = CURRENT_TIMESTAMP 
        WHERE uuid = ?;
        """;

    private static final String BATCH_UPDATE_QUERY = """
        INSERT INTO hodinovac_playtime (uuid, player_name, total_playtime, last_login, last_logout)
        VALUES (?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
        total_playtime = VALUES(total_playtime),
        last_logout = VALUES(last_logout),
        updated_at = CURRENT_TIMESTAMP;
        """;

    /**
     * Constructor for MySQLDatabase
     *
     * @param plugin Main plugin instance
     */
    public MySQLDatabase(Hodinovac plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes the database connection and creates tables
     *
     * @return CompletableFuture<Boolean> true if initialization was successful
     */
    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                plugin.getDebugger().log("Initializing MySQL database connection...");

                // Setup HikariCP connection pool
                HikariConfig config = new HikariConfig();
                config.setJdbcUrl(plugin.getConfigManager().getMySQLUrl());
                config.setUsername(plugin.getConfigManager().getMySQLUser());
                config.setPassword(plugin.getConfigManager().getMySQLPassword());

                // Connection pool settings
                config.setMaximumPoolSize(10);
                config.setMinimumIdle(2);
                config.setConnectionTimeout(30000);
                config.setIdleTimeout(600000);
                config.setMaxLifetime(1800000);
                config.setLeakDetectionThreshold(60000);

                // Connection properties for better performance
                config.addDataSourceProperty("cachePrepStmts", "true");
                config.addDataSourceProperty("prepStmtCacheSize", "250");
                config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                config.addDataSourceProperty("useServerPrepStmts", "true");
                config.addDataSourceProperty("useLocalSessionState", "true");
                config.addDataSourceProperty("rewriteBatchedStatements", "true");
                config.addDataSourceProperty("cacheResultSetMetadata", "true");
                config.addDataSourceProperty("cacheServerConfiguration", "true");
                config.addDataSourceProperty("elideSetAutoCommits", "true");
                config.addDataSourceProperty("maintainTimeStats", "false");

                this.dataSource = new HikariDataSource(config);

                // Test connection and create tables
                try (Connection connection = dataSource.getConnection()) {
                    plugin.getDebugger().log("Database connection established successfully");

                    // Create tables if they don't exist
                    try (PreparedStatement statement = connection.prepareStatement(CREATE_TABLE_QUERY)) {
                        statement.executeUpdate();
                        plugin.getDebugger().log("Database tables created/verified successfully");
                    }
                }

                plugin.getDebugger().log("MySQL database initialized successfully");
                return true;

            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to initialize MySQL database: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }, plugin.getExecutorService());
    }

    /**
     * Saves player data to database asynchronously
     *
     * @param playerUUID Player's UUID
     * @param playerName Player's name
     * @param totalPlaytime Total playtime in seconds
     * @param lastLogin Last login timestamp
     * @param lastLogout Last logout timestamp
     * @return CompletableFuture<Boolean> true if save was successful
     */
    public CompletableFuture<Boolean> savePlayerData(UUID playerUUID, String playerName,
                                                     long totalPlaytime, long lastLogin, long lastLogout) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(INSERT_OR_UPDATE_QUERY)) {

                statement.setString(1, playerUUID.toString());
                statement.setString(2, playerName);
                statement.setLong(3, totalPlaytime);
                statement.setLong(4, lastLogin);
                statement.setLong(5, lastLogout);

                int rowsAffected = statement.executeUpdate();

                plugin.getDebugger().log("Saved data for player " + playerName +
                        " (UUID: " + playerUUID + ") - Rows affected: " + rowsAffected);

                return rowsAffected > 0;

            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save player data for " + playerUUID + ": " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }, plugin.getExecutorService());
    }

    /**
     * Loads player's total playtime from database asynchronously
     *
     * @param playerUUID Player's UUID
     * @return CompletableFuture<Long> player's total playtime in seconds, 0 if not found
     */
    public CompletableFuture<Long> loadPlayerPlaytime(UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(SELECT_PLAYTIME_QUERY)) {

                statement.setString(1, playerUUID.toString());

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        long playtime = resultSet.getLong("total_playtime");
                        plugin.getDebugger().log("Loaded playtime for " + playerUUID + ": " + playtime + " seconds");
                        return playtime;
                    }
                }

                plugin.getDebugger().log("No playtime data found for " + playerUUID + ", returning 0");
                return 0L;

            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load player playtime for " + playerUUID + ": " + e.getMessage());
                e.printStackTrace();
                return 0L;
            }
        }, plugin.getExecutorService());
    }

    /**
     * Loads all player data from database asynchronously
     *
     * @param playerUUID Player's UUID
     * @return CompletableFuture<PlayerData> complete player data or null if not found
     */
    public CompletableFuture<PlayerData> loadPlayerData(UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(SELECT_ALL_DATA_QUERY)) {

                statement.setString(1, playerUUID.toString());

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        PlayerData data = new PlayerData(
                                UUID.fromString(resultSet.getString("uuid")),
                                resultSet.getString("player_name"),
                                resultSet.getLong("total_playtime"),
                                resultSet.getLong("last_login"),
                                resultSet.getLong("last_logout")
                        );

                        plugin.getDebugger().log("Loaded complete data for player " + data.playerName());
                        return data;
                    }
                }

                plugin.getDebugger().log("No data found for player " + playerUUID);
                return null;

            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load player data for " + playerUUID + ": " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }, plugin.getExecutorService());
    }

    /**
     * Updates player's playtime in database asynchronously
     *
     * @param playerUUID Player's UUID
     * @param newPlaytime New total playtime in seconds
     * @return CompletableFuture<Boolean> true if update was successful
     */
    public CompletableFuture<Boolean> updatePlaytime(UUID playerUUID, long newPlaytime) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(UPDATE_PLAYTIME_QUERY)) {

                statement.setLong(1, newPlaytime);
                statement.setString(2, playerUUID.toString());

                int rowsAffected = statement.executeUpdate();

                plugin.getDebugger().log("Updated playtime for " + playerUUID +
                        " to " + newPlaytime + " seconds - Rows affected: " + rowsAffected);

                return rowsAffected > 0;

            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to update playtime for " + playerUUID + ": " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }, plugin.getExecutorService());
    }

    /**
     * Batch saves multiple players' data for better performance
     *
     * @param playerDataMap Map of UUID to PlayerData for batch saving
     * @return CompletableFuture<Boolean> true if batch save was successful
     */
    public CompletableFuture<Boolean> batchSavePlayerData(Map<UUID, PlayerData> playerDataMap) {
        return CompletableFuture.supplyAsync(() -> {
            if (playerDataMap.isEmpty()) {
                return true;
            }

            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);

                try (PreparedStatement statement = connection.prepareStatement(BATCH_UPDATE_QUERY)) {

                    for (PlayerData data : playerDataMap.values()) {
                        statement.setString(1, data.uuid().toString());
                        statement.setString(2, data.playerName());
                        statement.setLong(3, data.totalPlaytime());
                        statement.setLong(4, data.lastLogin());
                        statement.setLong(5, data.lastLogout());
                        statement.addBatch();
                    }

                    int[] results = statement.executeBatch();
                    connection.commit();

                    plugin.getDebugger().log("Batch saved " + results.length + " player records");
                    return true;

                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to batch save player data: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }, plugin.getExecutorService());
    }

    /**
     * Refreshes database configuration (used when config is reloaded)
     */
    public void refreshConfig() {
        plugin.getDebugger().log("Refreshing database configuration...");
        // Note: For production use, you might want to recreate the connection pool
        // with new settings, but for simplicity we'll just log this
        plugin.getDebugger().log("Database configuration refresh completed");
    }

    /**
     * Closes the database connection pool
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            plugin.getDebugger().log("Closing database connection pool...");
            dataSource.close();
            plugin.getDebugger().log("Database connection pool closed");
        }
    }

    /**
     * Record class for player data
     *
     * @param uuid Player's UUID
     * @param playerName Player's name
     * @param totalPlaytime Total playtime in seconds
     * @param lastLogin Last login timestamp
     * @param lastLogout Last logout timestamp
     */
    public record PlayerData(UUID uuid, String playerName, long totalPlaytime,
                             long lastLogin, long lastLogout) {}
}