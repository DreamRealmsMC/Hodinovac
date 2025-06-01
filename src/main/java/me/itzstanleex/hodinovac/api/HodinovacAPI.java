package me.itzstanleex.hodinovac.api;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public API interface for Hodinovac playtime tracking plugin.
 *
 * This interface provides methods for other plugins to interact with
 * Hodinovac's playtime tracking system. All methods are thread-safe
 * and can be called from any thread.
 *
 * To get an instance of this API:
 * <pre>
 * HodinovacAPI api = Hodinovac.getAPI();
 * if (api != null) {
 *     long playtime = api.getPlaytime(playerUUID);
 *     // ... use the API
 * }
 * </pre>
 *
 * @author ItzStanleex
 * @version 1.0.0
 * @since 1.0.0
 */
public interface HodinovacAPI {

    /**
     * Gets a player's total playtime in seconds (excluding AFK time).
     *
     * This method returns the cached value for online players,
     * or loads from database for offline players.
     *
     * @param playerUUID The UUID of the player
     * @return Total playtime in seconds, 0 if player not found
     * @throws IllegalArgumentException if playerUUID is null
     * @since 1.0.0
     */
    long getPlaytime(UUID playerUUID);

    /**
     * Gets a player's total playtime asynchronously.
     *
     * This method is recommended for offline players as it may
     * require a database query. For online players, it returns
     * the cached value immediately.
     *
     * @param playerUUID The UUID of the player
     * @return CompletableFuture containing total playtime in seconds
     * @throws IllegalArgumentException if playerUUID is null
     * @since 1.0.0
     */
    CompletableFuture<Long> getPlaytimeAsync(UUID playerUUID);

    /**
     * Gets a formatted string representation of a player's total playtime.
     *
     * The format is determined by the plugin's configuration:
     * - longFormat = false: uses 'playtime_format_short' from config
     * - longFormat = true: uses 'playtime_format_long' from config
     *
     * Example formats:
     * - Short: "5h 23m"
     * - Long: "0d 5h 23m 45s"
     *
     * @param playerUUID The UUID of the player
     * @param longFormat true for long format, false for short format
     * @return Formatted playtime string
     * @throws IllegalArgumentException if playerUUID is null
     * @since 1.0.0
     */
    String getFormattedPlaytime(UUID playerUUID, boolean longFormat);

    /**
     * Gets a player's current session playtime in seconds.
     *
     * Session playtime is the time played since the player logged in,
     * excluding any AFK time during the current session.
     *
     * @param playerUUID The UUID of the player
     * @return Session playtime in seconds, 0 if player is offline or not found
     * @throws IllegalArgumentException if playerUUID is null
     * @since 1.0.0
     */
    long getSessionPlaytime(UUID playerUUID);

    /**
     * Gets a formatted string representation of a player's session playtime.
     *
     * @param playerUUID The UUID of the player
     * @param longFormat true for long format, false for short format
     * @return Formatted session playtime string
     * @throws IllegalArgumentException if playerUUID is null
     * @since 1.0.0
     */
    String getFormattedSessionPlaytime(UUID playerUUID, boolean longFormat);

    /**
     * Checks if a player is currently AFK (Away From Keyboard).
     *
     * A player is considered AFK if they haven't moved for the
     * configured amount of time (afk_timeout_seconds in config).
     *
     * @param playerUUID The UUID of the player
     * @return true if player is AFK, false if active or offline
     * @throws IllegalArgumentException if playerUUID is null
     * @since 1.0.0
     */
    boolean isAfk(UUID playerUUID);

    /**
     * Gets the time in milliseconds since a player last moved.
     *
     * This can be used to implement custom AFK detection or
     * activity monitoring features.
     *
     * @param playerUUID The UUID of the player
     * @return Time since last move in milliseconds, -1 if player is offline or not tracked
     * @throws IllegalArgumentException if playerUUID is null
     * @since 1.0.0
     */
    long getTimeSinceLastMove(UUID playerUUID);

    /**
     * Adds playtime to a player's total playtime.
     *
     * This method is useful for admin commands, rewards systems,
     * or integrations with other plugins. The added time is
     * immediately reflected in the player's total playtime.
     *
     * @param playerUUID The UUID of the player
     * @param seconds The number of seconds to add (must be positive)
     * @return true if the operation was successful, false otherwise
     * @throws IllegalArgumentException if playerUUID is null or seconds is negative
     * @since 1.0.0
     */
    boolean addPlaytime(UUID playerUUID, long seconds);

    /**
     * Removes playtime from a player's total playtime.
     *
     * This method is useful for admin commands or punishment systems.
     * The player's total playtime will not go below 0.
     *
     * @param playerUUID The UUID of the player
     * @param seconds The number of seconds to remove (must be positive)
     * @return true if the operation was successful, false otherwise
     * @throws IllegalArgumentException if playerUUID is null or seconds is negative
     * @since 1.0.0
     */
    boolean removePlaytime(UUID playerUUID, long seconds);

    /**
     * Manually sets a player's AFK status.
     *
     * This method allows other plugins to control a player's AFK status.
     * When setting a player as not AFK, their last move time is updated.
     *
     * @param playerUUID The UUID of the player
     * @param afk true to mark as AFK, false to mark as active
     * @return true if the operation was successful, false if player is offline
     * @throws IllegalArgumentException if playerUUID is null
     * @since 1.0.0
     */
    boolean setAfkStatus(UUID playerUUID, boolean afk);

    /**
     * Checks if a player exists in the plugin's database.
     *
     * This method performs a database lookup and should be called
     * asynchronously for best performance.
     *
     * @param playerUUID The UUID of the player
     * @return CompletableFuture containing true if player exists in database
     * @throws IllegalArgumentException if playerUUID is null
     * @since 1.0.0
     */
    CompletableFuture<Boolean> hasPlaytimeData(UUID playerUUID);

    /**
     * Gets the current AFK timeout in seconds.
     *
     * This is the configured time after which a player is
     * considered AFK if they haven't moved.
     *
     * @return AFK timeout in seconds
     * @since 1.0.0
     */
    long getAfkTimeoutSeconds();

    /**
     * Checks if the plugin is currently tracking a player.
     *
     * A player is tracked if they are online and have been
     * initialized by the plugin.
     *
     * @param playerUUID The UUID of the player
     * @return true if player is currently being tracked, false otherwise
     * @throws IllegalArgumentException if playerUUID is null
     * @since 1.0.0
     */
    boolean isPlayerTracked(UUID playerUUID);

    /**
     * Forces a save of a player's data to the database.
     *
     * This method immediately saves the player's current playtime
     * data to the database, bypassing the normal sync interval.
     *
     * @param playerUUID The UUID of the player
     * @return CompletableFuture containing true if save was successful
     * @throws IllegalArgumentException if playerUUID is null
     * @since 1.0.0
     */
    CompletableFuture<Boolean> savePlayerData(UUID playerUUID);

    /**
     * Gets the plugin version.
     *
     * @return Plugin version string
     * @since 1.0.0
     */
    String getPluginVersion();

    /**
     * Checks if the API is available and ready to use.
     *
     * The API may not be available during plugin startup or shutdown.
     *
     * @return true if API is ready, false otherwise
     * @since 1.0.0
     */
    boolean isAvailable();
}