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
 * AFK (Away From Keyboard) detection manager for Hodinovac plugin.
 *
 * Monitors player movement events to detect when players become AFK.
 * Only movement resets AFK status - chat, interactions, and other events do not.
 * Fires PlayerAfkStatusChangeEvent when AFK status changes.
 *
 * @author ItzStanleyX
 * @version 1.0.0
 */
public class AfkManager implements Listener {

    private final Hodinovac plugin;
    private final ConcurrentMap<UUID, Long> lastMoveTime;
    private final ConcurrentMap<UUID, Boolean> afkStatus;

    private BukkitTask afkCheckTask;
    private long afkTimeoutMillis;

    /**
     * Constructor for AfkManager
     *
     * @param plugin Main plugin instance
     */
    public AfkManager(Hodinovac plugin) {
        this.plugin = plugin;
        this.lastMoveTime = new ConcurrentHashMap<>();
        this.afkStatus = new ConcurrentHashMap<>();

        loadConfig();
        registerEvents();
        startAfkCheckTask();

        plugin.getDebugger().log("AfkManager initialized with " + (afkTimeoutMillis / 1000) + "s timeout");
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
     * Starts the periodic AFK check task
     */
    private void startAfkCheckTask() {
        // Check for AFK players every 10 seconds
        this.afkCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkAfkPlayers, 200L, 200L);
        plugin.getDebugger().log("Started AFK check task with 10-second intervals");
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

        // Load player data into cache
        plugin.getPlaytimeCache().loadPlayerData(playerUUID, player.getName());

        plugin.getDebugger().log("Initialized AFK tracking for player: " + player.getName());
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

        // Unload player data from cache
        plugin.getPlaytimeCache().unloadPlayerData(playerUUID);

        plugin.getDebugger().log("Cleaned up AFK tracking for player: " + player.getName());
    }

    /**
     * Handles player movement events for AFK detection
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

        // Update last move time
        lastMoveTime.put(playerUUID, currentTime);

        // Update cache
        plugin.getPlaytimeCache().updateLastMoveTime(playerUUID);

        // Check if player was AFK and is now active
        Boolean wasAfk = afkStatus.get(playerUUID);
        if (wasAfk != null && wasAfk) {
            setAfkStatus(playerUUID, false);
        }

        plugin.getDebugger().log("Player " + player.getName() + " moved, updated last move time");
    }

    /**
     * Periodically checks all online players for AFK status
     */
    private void checkAfkPlayers() {
        long currentTime = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerUUID = player.getUniqueId();

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
                setAfkStatus(playerUUID, shouldBeAfk);

                plugin.getDebugger().log("Player " + player.getName() +
                        (shouldBeAfk ? " is now AFK" : " is no longer AFK") +
                        " (inactive for " + (timeSinceLastMove / 1000) + "s)");
            }
        }
    }

    /**
     * Sets a player's AFK status and fires the appropriate event
     *
     * @param playerUUID Player's UUID
     * @param afk New AFK status
     */
    private void setAfkStatus(UUID playerUUID, boolean afk) {
        Boolean previousStatus = afkStatus.put(playerUUID, afk);

        // Only fire event if status actually changed
        if (previousStatus == null || previousStatus != afk) {
            // Update cache
            plugin.getPlaytimeCache().setAfkStatus(playerUUID, afk, false);

            // Fire event on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                PlayerAfkStatusChangeEvent event = new PlayerAfkStatusChangeEvent(playerUUID, afk);
                Bukkit.getPluginManager().callEvent(event);

                plugin.getDebugger().log("Fired PlayerAfkStatusChangeEvent for " + playerUUID +
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
        }

        setAfkStatus(playerUUID, afk);

        plugin.getDebugger().log("Manually set AFK status for " + playerUUID + " to " + afk);
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
        plugin.getDebugger().log("Refreshing AFK manager configuration...");

        long oldTimeout = this.afkTimeoutMillis;
        loadConfig();

        if (oldTimeout != this.afkTimeoutMillis) {
            plugin.getDebugger().log("AFK timeout changed from " + (oldTimeout / 1000) +
                    "s to " + (afkTimeoutMillis / 1000) + "s");
        }

        plugin.getDebugger().log("AFK manager configuration refreshed");
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
     * Shuts down the AFK manager
     */
    public void shutdown() {
        plugin.getDebugger().log("Shutting down AFK manager...");

        // Cancel the AFK check task
        if (afkCheckTask != null && !afkCheckTask.isCancelled()) {
            afkCheckTask.cancel();
        }

        // Clear data
        lastMoveTime.clear();
        afkStatus.clear();

        plugin.getDebugger().log("AFK manager shutdown completed");
    }
}