package me.itzstanleex.hodinovac.cache;

import lombok.Getter;
import lombok.Setter;
import me.itzstanleex.hodinovac.Hodinovac;
import me.itzstanleex.hodinovac.database.MySQLDatabase;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory cache manager for player playtime data.
 *
 * Maintains fast access to player playtime and AFK status in memory,
 * with periodic synchronization to the database. Handles session tracking
 * and provides thread-safe operations for all playtime modifications.
 *
 * @author ItzStanleyX
 * @version 1.0.0
 */
public class PlaytimeCache {

    private final Hodinovac plugin;
    private final Map<UUID, CachedPlayerData> playerCache;

    @Getter
    private BukkitTask syncTask;

    @Getter
    private BukkitTask playtimeUpdateTask;

    /**
     * Constructor for PlaytimeCache
     *
     * @param plugin Main plugin instance
     */
    public PlaytimeCache(Hodinovac plugin) {
        this.plugin = plugin;
        this.playerCache = new ConcurrentHashMap<>();
    }

    /**
     * Loads player data into cache when they join
     *
     * @param playerUUID Player's UUID
     * @param playerName Player's name
     */
    public void loadPlayerData(UUID playerUUID, String playerName) {
        plugin.getDebugger().log("Loading player data into cache for: " + playerName);

        // Load from database asynchronously
        plugin.getDatabase().loadPlayerData(playerUUID).thenAccept(data -> {
            long totalPlaytime = data != null ? data.totalPlaytime() : 0L;
            long currentTime = System.currentTimeMillis();

            // Create cache entry
            CachedPlayerData cachedData = new CachedPlayerData(
                    playerUUID,
                    playerName,
                    totalPlaytime,
                    0L, // session playtime starts at 0
                    currentTime, // login time
                    0L, // logout time (not set yet)
                    false, // not AFK initially
                    currentTime, // last move time
                    true // mark as dirty for initial save
            );

            playerCache.put(playerUUID, cachedData);

            plugin.getDebugger().log("Loaded player " + playerName + " with total playtime: " + totalPlaytime + " seconds");
        }).exceptionally(throwable -> {
            plugin.getLogger().severe("Failed to load data for player " + playerName + ": " + throwable.getMessage());

            // Create default entry even if DB load fails
            long currentTime = System.currentTimeMillis();
            CachedPlayerData cachedData = new CachedPlayerData(
                    playerUUID,
                    playerName,
                    0L,
                    0L,
                    currentTime,
                    0L,
                    false,
                    currentTime,
                    true
            );

            playerCache.put(playerUUID, cachedData);
            return null;
        });
    }

    /**
     * Saves and removes player data from cache when they leave
     *
     * @param playerUUID Player's UUID
     */
    public void unloadPlayerData(UUID playerUUID) {
        CachedPlayerData data = playerCache.get(playerUUID);
        if (data == null) {
            return;
        }

        plugin.getDebugger().log("Unloading player data from cache for: " + data.getPlayerName());

        // Update logout time and add session playtime to total
        data.setLogoutTime(System.currentTimeMillis());
        data.addToTotalPlaytime(data.getSessionPlaytime());
        data.setSessionPlaytime(0L);
        data.setDirty(true);

        // Save to database immediately
        savePlayerToDatabase(data).thenRun(() -> {
            playerCache.remove(playerUUID);
            plugin.getDebugger().log("Player data saved and removed from cache: " + data.getPlayerName());
        });
    }

    /**
     * Gets player's total playtime from cache
     *
     * @param playerUUID Player's UUID
     * @return Total playtime in seconds, 0 if not found
     */
    public long getTotalPlaytime(UUID playerUUID) {
        CachedPlayerData data = playerCache.get(playerUUID);
        return data != null ? data.getTotalPlaytime() + data.getSessionPlaytime() : 0L;
    }

    /**
     * Gets player's current session playtime from cache
     *
     * @param playerUUID Player's UUID
     * @return Session playtime in seconds, 0 if not found
     */
    public long getSessionPlaytime(UUID playerUUID) {
        CachedPlayerData data = playerCache.get(playerUUID);
        return data != null ? data.getSessionPlaytime() : 0L;
    }

    /**
     * Gets player's AFK status from cache
     *
     * @param playerUUID Player's UUID
     * @return true if player is AFK, false otherwise
     */
    public boolean isAfk(UUID playerUUID) {
        CachedPlayerData data = playerCache.get(playerUUID);
        return data != null && data.isAfk();
    }

    /**
     * Sets player's AFK status and updates last move time
     *
     * @param playerUUID Player's UUID
     * @param afk New AFK status
     * @param updateMoveTime Whether to update last move time
     */
    public void setAfkStatus(UUID playerUUID, boolean afk, boolean updateMoveTime) {
        CachedPlayerData data = playerCache.get(playerUUID);
        if (data == null) {
            return;
        }

        boolean wasAfk = data.isAfk();
        data.setAfk(afk);

        if (updateMoveTime) {
            data.setLastMoveTime(System.currentTimeMillis());
        }

        data.setDirty(true);

        plugin.getDebugger().log("Updated AFK status for " + data.getPlayerName() +
                ": " + wasAfk + " -> " + afk);
    }

    /**
     * Updates last move time for a player (used for AFK detection)
     *
     * @param playerUUID Player's UUID
     */
    public void updateLastMoveTime(UUID playerUUID) {
        CachedPlayerData data = playerCache.get(playerUUID);
        if (data != null) {
            data.setLastMoveTime(System.currentTimeMillis());

            // If player was AFK and just moved, mark as not AFK
            if (data.isAfk()) {
                setAfkStatus(playerUUID, false, false);
            }
        }
    }

    /**
     * Gets last move time for a player
     *
     * @param playerUUID Player's UUID
     * @return Last move timestamp, 0 if not found
     */
    public long getLastMoveTime(UUID playerUUID) {
        CachedPlayerData data = playerCache.get(playerUUID);
        return data != null ? data.getLastMoveTime() : 0L;
    }

    /**
     * Adds playtime to a player (for admin commands)
     *
     * @param playerUUID Player's UUID
     * @param seconds Seconds to add
     */
    public void addPlaytime(UUID playerUUID, long seconds) {
        CachedPlayerData data = playerCache.get(playerUUID);
        if (data != null) {
            data.addToTotalPlaytime(seconds);
            data.setDirty(true);
            plugin.getDebugger().log("Added " + seconds + " seconds to " + data.getPlayerName());
        }
    }

    /**
     * Removes playtime from a player (for admin commands)
     *
     * @param playerUUID Player's UUID
     * @param seconds Seconds to remove
     */
    public void removePlaytime(UUID playerUUID, long seconds) {
        CachedPlayerData data = playerCache.get(playerUUID);
        if (data != null) {
            data.subtractFromTotalPlaytime(seconds);
            data.setDirty(true);
            plugin.getDebugger().log("Removed " + seconds + " seconds from " + data.getPlayerName());
        }
    }

    /**
     * Starts the periodic sync task and playtime update task
     */
    public void startSyncTask() {
        long updateInterval = plugin.getConfigManager().getPlaytimeUpdateInterval();

        // Task to update session playtime every second for non-AFK players
        this.playtimeUpdateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (CachedPlayerData data : playerCache.values()) {
                if (!data.isAfk()) {
                    data.addToSessionPlaytime(1L);
                    data.setDirty(true);
                }
            }
        }, 20L, 20L); // Run every second (20 ticks)

        // Task to sync dirty data to database
        this.syncTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            syncDirtyDataToDatabase();
        }, updateInterval * 20L, updateInterval * 20L);

        plugin.getDebugger().log("Started playtime update and sync tasks with " + updateInterval + "s interval");
    }

    /**
     * Syncs all dirty player data to database
     */
    private void syncDirtyDataToDatabase() {
        Map<UUID, MySQLDatabase.PlayerData> dirtyData = new ConcurrentHashMap<>();

        for (CachedPlayerData data : playerCache.values()) {
            if (data.isDirty()) {
                MySQLDatabase.PlayerData dbData = new MySQLDatabase.PlayerData(
                        data.getUuid(),
                        data.getPlayerName(),
                        data.getTotalPlaytime() + data.getSessionPlaytime(),
                        data.getLoginTime(),
                        data.getLogoutTime()
                );

                dirtyData.put(data.getUuid(), dbData);
                data.setDirty(false);
            }
        }

        if (!dirtyData.isEmpty()) {
            plugin.getDatabase().batchSavePlayerData(dirtyData).thenAccept(success -> {
                if (success) {
                    plugin.getDebugger().log("Synced " + dirtyData.size() + " dirty player records to database");
                } else {
                    plugin.getLogger().warning("Failed to sync some player data to database");
                    // Mark as dirty again for retry
                    for (UUID uuid : dirtyData.keySet()) {
                        CachedPlayerData data = playerCache.get(uuid);
                        if (data != null) {
                            data.setDirty(true);
                        }
                    }
                }
            });
        }
    }

    /**
     * Saves a single player to database
     *
     * @param data Player data to save
     * @return CompletableFuture indicating save success
     */
    private java.util.concurrent.CompletableFuture<Boolean> savePlayerToDatabase(CachedPlayerData data) {
        return plugin.getDatabase().savePlayerData(
                data.getUuid(),
                data.getPlayerName(),
                data.getTotalPlaytime(),
                data.getLoginTime(),
                data.getLogoutTime()
        );
    }

    /**
     * Shuts down the cache and saves all data
     */
    public void shutdown() {
        plugin.getDebugger().log("Shutting down playtime cache...");

        // Cancel tasks
        if (playtimeUpdateTask != null && !playtimeUpdateTask.isCancelled()) {
            playtimeUpdateTask.cancel();
        }

        if (syncTask != null && !syncTask.isCancelled()) {
            syncTask.cancel();
        }

        // Save all cached data synchronously during shutdown
        for (CachedPlayerData data : playerCache.values()) {
            // Add current session to total
            data.addToTotalPlaytime(data.getSessionPlaytime());
            data.setLogoutTime(System.currentTimeMillis());

            try {
                plugin.getDatabase().savePlayerData(
                        data.getUuid(),
                        data.getPlayerName(),
                        data.getTotalPlaytime(),
                        data.getLoginTime(),
                        data.getLogoutTime()
                ).get(); // Wait for completion

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to save data for " + data.getPlayerName() +
                        " during shutdown: " + e.getMessage());
            }
        }

        playerCache.clear();
        plugin.getDebugger().log("Playtime cache shutdown completed");
    }

    /**
     * Gets all cached player UUIDs
     *
     * @return Set of UUIDs currently in cache
     */
    public java.util.Set<UUID> getCachedPlayers() {
        return playerCache.keySet();
    }

    /**
     * Internal class for cached player data
     */
    @Getter
    @Setter
    private static class CachedPlayerData {
        private final UUID uuid;
        private final String playerName;
        private final AtomicLong totalPlaytime;
        private final AtomicLong sessionPlaytime;
        private final long loginTime;
        private volatile long logoutTime;
        private volatile boolean afk;
        private volatile long lastMoveTime;
        private volatile boolean dirty;

        public CachedPlayerData(UUID uuid, String playerName, long totalPlaytime,
                                long sessionPlaytime, long loginTime, long logoutTime,
                                boolean afk, long lastMoveTime, boolean dirty) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.totalPlaytime = new AtomicLong(totalPlaytime);
            this.sessionPlaytime = new AtomicLong(sessionPlaytime);
            this.loginTime = loginTime;
            this.logoutTime = logoutTime;
            this.afk = afk;
            this.lastMoveTime = lastMoveTime;
            this.dirty = dirty;
        }

        public long getTotalPlaytime() {
            return totalPlaytime.get();
        }

        public long getSessionPlaytime() {
            return sessionPlaytime.get();
        }

        public void addToTotalPlaytime(long seconds) {
            totalPlaytime.addAndGet(seconds);
        }

        public void subtractFromTotalPlaytime(long seconds) {
            totalPlaytime.updateAndGet(current -> Math.max(0, current - seconds));
        }

        public void addToSessionPlaytime(long seconds) {
            sessionPlaytime.addAndGet(seconds);
        }

        public void setSessionPlaytime(long seconds) {
            sessionPlaytime.set(seconds);
        }
    }
}