package me.itzstanleex.hodinovac.afk;

import me.itzstanleex.hodinovac.Hodinovac;
import me.itzstanleex.hodinovac.api.events.PlayerAfkStatusChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Performance-optimized AFK detection manager.
 *
 * Key optimizations:
 * - Increased check interval from 10s to 30s (3x less CPU usage)
 * - Direct integration with optimized cache via event-driven updates
 * - Minimal main thread impact with batch processing
 * - Smart movement detection to reduce unnecessary updates
 *
 * @author ItzStanleex
 * @version 2.0.0 (Performance Optimized)
 */
public class AfkManager implements Listener {

    private final Hodinovac plugin;
    private final ConcurrentMap<UUID, Long> lastMoveTime;
    private final ConcurrentMap<UUID, Boolean> afkStatus;

    private BukkitTask optimizedAfkCheckTask;
    private long afkTimeoutMillis;

    // Performance optimization: reduce movement event spam
    private final ConcurrentMap<UUID, Long> lastMoveUpdateTime;
    private static final long MOVE_UPDATE_COOLDOWN = 5000; // 5 seconds

    /**
     * Constructor for optimized AfkManager
     *
     * @param plugin Main plugin instance
     */
    public AfkManager(Hodinovac plugin) {
        this.plugin = plugin;
        this.lastMoveTime = new ConcurrentHashMap<>();
        this.afkStatus = new ConcurrentHashMap<>();
        this.lastMoveUpdateTime = new ConcurrentHashMap<>();

        loadConfig();
        registerEvents();
        startOptimizedAfkCheckTask();

        plugin.getDebugger().log("OPTIMIZED AfkManager initialized with " + (afkTimeoutMillis / 1000) + "s timeout");
    }

    /**
     * Loads configuration settings
     */
    private void loadConfig() {
        this.afkTimeoutMillis = plugin.getConfigManager().getAfkTimeoutSeconds() * 1000L;
    }

    /**
     * Registers event listeners
     */
    private void registerEvents() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Starts the OPTIMIZED AFK check task with longer intervals
     * 30 seconds instead of 10 seconds = 3x better performance
     */
    private void startOptimizedAfkCheckTask() {
        // OPTIMIZATION: Check every 30 seconds instead of 10 seconds
        this.optimizedAfkCheckTask = Bukkit.getScheduler().runTaskTimer(plugin,
                this::checkAfkPlayersOptimized, 600L, 600L); // 30 seconds = 600 ticks

        plugin.getDebugger().log("Started OPTIMIZED AFK check task with 30-second intervals (3x better performance)");
    }

    /**
     * Handles player join events
     *
     * @param event PlayerJoinEvent
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // Initialize player data
        lastMoveTime.put(playerUUID, currentTime);
        afkStatus.put(playerUUID, false);
        lastMoveUpdateTime.put(playerUUID, 0L);

        // Load player data into optimized cache
        plugin.getPlaytimeCache().loadPlayerData(playerUUID, player.getName());

        plugin.getDebugger().log("Initialized OPTIMIZED AFK tracking for player: " + player.getName());
    }

    /**
     * Handles player quit events
     *
     * @param event PlayerQuitEvent
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Clean up player data
        lastMoveTime.remove(playerUUID);
        afkStatus.remove(playerUUID);
        lastMoveUpdateTime.remove(playerUUID);

        // Unload player data from optimized cache
        plugin.getPlaytimeCache().unloadPlayerData(playerUUID);

        plugin.getDebugger().log("Cleaned up OPTIMIZED AFK tracking for player: " + player.getName());
    }

    /**
     * OPTIMIZED movement handler with cooldown to reduce spam
     *
     * @param event PlayerMoveEvent
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only track actual movement, not just looking around
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // OPTIMIZATION: Cooldown to prevent movement spam
        Long lastUpdate = lastMoveUpdateTime.get(playerUUID);
        if (lastUpdate != null && (currentTime - lastUpdate) < MOVE_UPDATE_COOLDOWN) {
            // Update move time but don't trigger other events yet
            lastMoveTime.put(playerUUID, currentTime);
            return;
        }

        // Update last move time
        lastMoveTime.put(playerUUID, currentTime);
        lastMoveUpdateTime.put(playerUUID, currentTime);

        // Check if player was AFK and is now active
        Boolean wasAfk = afkStatus.get(playerUUID);
        if (wasAfk != null && wasAfk) {
            // OPTIMIZED: Direct integration with cache
            setAfkStatusOptimized(playerUUID, false);
        }

        plugin.getDebugger().log("Player " + player.getName() + " moved (optimized tracking)");
    }

    /**
     * OPTIMIZED AFK checking with batch processing and longer intervals
     */
    private void checkAfkPlayersOptimized() {
        long currentTime = System.currentTimeMillis();
        int checkedPlayers = 0;
        int statusChanges = 0;

        // Batch process all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerUUID = player.getUniqueId();
            checkedPlayers++;

            // Get last move time
            Long lastMove = lastMoveTime.get(playerUUID);
            if (lastMove == null) {
                continue;
            }

            // Check if player has been inactive for too long
            long timeSinceLastMove = currentTime - lastMove;
            boolean shouldBeAfk = timeSinceLastMove >= afkTimeoutMillis;

            // Get current AFK status
            Boolean currentAfkStatus = afkStatus.get(playerUUID);
            boolean isCurrentlyAfk = currentAfkStatus != null && currentAfkStatus;

            // Update AFK status if it changed
            if (shouldBeAfk != isCurrentlyAfk) {
                setAfkStatusOptimized(playerUUID, shouldBeAfk);
                statusChanges++;

                plugin.getDebugger().log("OPTIMIZED: Player " + player.getName() +
                        (shouldBeAfk ? " is now AFK" : " is no longer AFK") +
                        " (inactive for " + (timeSinceLastMove / 1000) + "s)");
            }
        }

        // Performance logging
        if (checkedPlayers > 0) {
            plugin.getDebugger().log("OPTIMIZED AFK Check: " + checkedPlayers + " players checked, " +
                    statusChanges + " status changes");
        }
    }

    /**
     * OPTIMIZED AFK status setter with direct cache integration
     *
     * @param playerUUID Player's UUID
     * @param afk New AFK status
     */
    private void setAfkStatusOptimized(UUID playerUUID, boolean afk) {
        Boolean previousStatus = afkStatus.put(playerUUID, afk);

        // Only process if status actually changed
        if (previousStatus == null || previousStatus != afk) {
            // CORE OPTIMIZATION: Direct integration with optimized cache
            plugin.getPlaytimeCache().onAfkStatusChange(playerUUID, afk);

            // Fire event on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                PlayerAfkStatusChangeEvent event = new PlayerAfkStatusChangeEvent(playerUUID, afk);
                Bukkit.getPluginManager().callEvent(event);

                plugin.getDebugger().log("OPTIMIZED: Fired PlayerAfkStatusChangeEvent for " + playerUUID +
                        ": " + previousStatus + " -> " + afk);
            });
        }
    }

    /**
     * Gets a player's current AFK status
     *
     * @param playerUUID Player's UUID
     * @return true if player is AFK, false otherwise
     */
    public boolean isPlayerAfk(UUID playerUUID) {
        Boolean status = afkStatus.get(playerUUID);
        return status != null && status;
    }

    /**
     * Gets a player's last move time
     *
     * @param playerUUID Player's UUID
     * @return Last move timestamp in milliseconds, 0 if not tracked
     */
    public long getLastMoveTime(UUID playerUUID) {
        Long time = lastMoveTime.get(playerUUID);
        return time != null ? time : 0L;
    }

    /**
     * Gets the time since a player last moved
     *
     * @param playerUUID Player's UUID
     * @return Time since last move in milliseconds, -1 if player not tracked
     */
    public long getTimeSinceLastMove(UUID playerUUID) {
        Long lastMove = lastMoveTime.get(playerUUID);
        if (lastMove == null) {
            return -1L;
        }

        return System.currentTimeMillis() - lastMove;
    }

    /**
     * Manually sets a player's AFK status (for admin commands or API)
     *
     * @param playerUUID Player's UUID
     * @param afk New AFK status
     * @param updateMoveTime Whether to update the last move time
     */
    public void setPlayerAfk(UUID playerUUID, boolean afk, boolean updateMoveTime) {
        if (updateMoveTime && !afk) {
            lastMoveTime.put(playerUUID, System.currentTimeMillis());
            lastMoveUpdateTime.put(playerUUID, System.currentTimeMillis());
        }

        setAfkStatusOptimized(playerUUID, afk);

        plugin.getDebugger().log("OPTIMIZED: Manually set AFK status for " + playerUUID + " to " + afk);
    }

    /**
     * Gets the current AFK timeout in milliseconds
     *
     * @return AFK timeout in milliseconds
     */
    public long getAfkTimeoutMillis() {
        return afkTimeoutMillis;
    }

    /**
     * Gets the current AFK timeout in seconds
     *
     * @return AFK timeout in seconds
     */
    public long getAfkTimeoutSeconds() {
        return afkTimeoutMillis / 1000L;
    }

    /**
     * Refreshes configuration (called when config is reloaded)
     */
    public void refreshConfig() {
        plugin.getDebugger().log("Refreshing OPTIMIZED AFK manager configuration...");

        long oldTimeout = this.afkTimeoutMillis;
        loadConfig();

        if (oldTimeout != this.afkTimeoutMillis) {
            plugin.getDebugger().log("OPTIMIZED: AFK timeout changed from " + (oldTimeout / 1000) +
                    "s to " + (afkTimeoutMillis / 1000) + "s");
        }

        plugin.getDebugger().log("OPTIMIZED AFK manager configuration refreshed");
    }

    /**
     * Gets the number of currently tracked players
     *
     * @return Number of players being tracked for AFK
     */
    public int getTrackedPlayerCount() {
        return lastMoveTime.size();
    }

    /**
     * Gets the number of currently AFK players
     *
     * @return Number of players currently marked as AFK
     */
    public int getAfkPlayerCount() {
        return (int) afkStatus.values().stream().mapToLong(afk -> afk ? 1L : 0L).sum();
    }

    /**
     * Gets performance statistics for monitoring
     *
     * @return Performance stats string
     */
    public String getPerformanceStats() {
        int totalTracked = getTrackedPlayerCount();
        int totalAfk = getAfkPlayerCount();
        long checkInterval = 30; // seconds

        return String.format("OPTIMIZED AFK: %d tracked, %d AFK, checks every %ds",
                totalTracked, totalAfk, checkInterval);
    }

    /**
     * Shuts down the optimized AFK manager
     */
    public void shutdown() {
        plugin.getDebugger().log("Shutting down OPTIMIZED AFK manager...");

        // Cancel the optimized AFK check task
        if (optimizedAfkCheckTask != null && !optimizedAfkCheckTask.isCancelled()) {
            optimizedAfkCheckTask.cancel();
        }

        // Clear data
        lastMoveTime.clear();
        afkStatus.clear();
        lastMoveUpdateTime.clear();

        plugin.getDebugger().log("OPTIMIZED AFK manager shutdown completed");
    }
}