package me.itzstanleex.hodinovac.api;

import me.itzstanleex.hodinovac.Hodinovac;
import me.itzstanleex.hodinovac.cache.PlaytimeCache;
import me.itzstanleex.hodinovac.database.MySQLDatabase;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of the HodinovacAPI interface.
 *
 * This class provides the actual implementation of all API methods,
 * delegating to the appropriate plugin components while ensuring
 * thread safety and proper error handling.
 *
 * @author ItzStanleex
 * @version 1.0.0
 * @since 1.0.0
 */
public class HodinovacAPIImpl implements HodinovacAPI {

    private final Hodinovac plugin;

    /**
     * Constructor for HodinovacAPIImpl
     *
     * @param plugin Main plugin instance
     */
    public HodinovacAPIImpl(Hodinovac plugin) {
        this.plugin = plugin;
    }

    @Override
    public long getPlaytime(UUID playerUUID) {
        validatePlayerUUID(playerUUID);

        if (!isAvailable()) {
            return 0L;
        }

        // Try to get from cache first (for online players)
        PlaytimeCache cache = plugin.getPlaytimeCache();
        if (cache != null && cache.getCachedPlayers().contains(playerUUID)) {
            return cache.getTotalPlaytime(playerUUID);
        }

        // For offline players, we need to load from database synchronously
        // This should be avoided in favor of getPlaytimeAsync for better performance
        try {
            return plugin.getDatabase().loadPlayerPlaytime(playerUUID).get();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get playtime for " + playerUUID + ": " + e.getMessage());
            return 0L;
        }
    }

    @Override
    public CompletableFuture<Long> getPlaytimeAsync(UUID playerUUID) {
        validatePlayerUUID(playerUUID);

        if (!isAvailable()) {
            return CompletableFuture.completedFuture(0L);
        }

        // Try to get from cache first (for online players)
        PlaytimeCache cache = plugin.getPlaytimeCache();
        if (cache != null && cache.getCachedPlayers().contains(playerUUID)) {
            return CompletableFuture.completedFuture(cache.getTotalPlaytime(playerUUID));
        }

        // Load from database asynchronously
        return plugin.getDatabase().loadPlayerPlaytime(playerUUID);
    }

    @Override
    public String getFormattedPlaytime(UUID playerUUID, boolean longFormat) {
        validatePlayerUUID(playerUUID);

        if (!isAvailable()) {
            return "0s";
        }

        long totalSeconds = getPlaytime(playerUUID);
        return formatPlaytime(totalSeconds, longFormat);
    }

    @Override
    public long getSessionPlaytime(UUID playerUUID) {
        validatePlayerUUID(playerUUID);

        if (!isAvailable()) {
            return 0L;
        }

        PlaytimeCache cache = plugin.getPlaytimeCache();
        if (cache != null) {
            return cache.getSessionPlaytime(playerUUID);
        }

        return 0L;
    }

    @Override
    public String getFormattedSessionPlaytime(UUID playerUUID, boolean longFormat) {
        validatePlayerUUID(playerUUID);

        if (!isAvailable()) {
            return "0s";
        }

        long sessionSeconds = getSessionPlaytime(playerUUID);
        return formatPlaytime(sessionSeconds, longFormat);
    }

    @Override
    public boolean isAfk(UUID playerUUID) {
        validatePlayerUUID(playerUUID);

        if (!isAvailable()) {
            return false;
        }

        PlaytimeCache cache = plugin.getPlaytimeCache();
        if (cache != null) {
            return cache.isAfk(playerUUID);
        }

        return false;
    }

    @Override
    public long getTimeSinceLastMove(UUID playerUUID) {
        validatePlayerUUID(playerUUID);

        if (!isAvailable()) {
            return -1L;
        }

        if (plugin.getAfkManager() != null) {
            return plugin.getAfkManager().getTimeSinceLastMove(playerUUID);
        }

        return -1L;
    }

    @Override
    public boolean addPlaytime(UUID playerUUID, long seconds) {
        validatePlayerUUID(playerUUID);
        validatePositiveSeconds(seconds);

        if (!isAvailable()) {
            return false;
        }

        try {
            PlaytimeCache cache = plugin.getPlaytimeCache();
            if (cache != null) {
                cache.addPlaytime(playerUUID, seconds);

                plugin.getDebugger().log("API: Added " + seconds + " seconds to player " + playerUUID);
                return true;
            }

            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to add playtime for " + playerUUID + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean removePlaytime(UUID playerUUID, long seconds) {
        validatePlayerUUID(playerUUID);
        validatePositiveSeconds(seconds);

        if (!isAvailable()) {
            return false;
        }

        try {
            PlaytimeCache cache = plugin.getPlaytimeCache();
            if (cache != null) {
                cache.removePlaytime(playerUUID, seconds);

                plugin.getDebugger().log("API: Removed " + seconds + " seconds from player " + playerUUID);
                return true;
            }

            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to remove playtime for " + playerUUID + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean setAfkStatus(UUID playerUUID, boolean afk) {
        validatePlayerUUID(playerUUID);

        if (!isAvailable()) {
            return false;
        }

        // Check if player is online
        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null || !player.isOnline()) {
            return false;
        }

        try {
            if (plugin.getAfkManager() != null) {
                plugin.getAfkManager().setPlayerAfk(playerUUID, afk, !afk);

                plugin.getDebugger().log("API: Set AFK status for " + playerUUID + " to " + afk);
                return true;
            }

            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to set AFK status for " + playerUUID + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public CompletableFuture<Boolean> hasPlaytimeData(UUID playerUUID) {
        validatePlayerUUID(playerUUID);

        if (!isAvailable()) {
            return CompletableFuture.completedFuture(false);
        }

        return plugin.getDatabase().loadPlayerData(playerUUID)
                .thenApply(data -> data != null);
    }

    @Override
    public long getAfkTimeoutSeconds() {
        if (!isAvailable()) {
            return 300L; // Default 5 minutes
        }

        if (plugin.getAfkManager() != null) {
            return plugin.getAfkManager().getAfkTimeoutSeconds();
        }

        return plugin.getConfigManager().getAfkTimeoutSeconds();
    }

    @Override
    public boolean isPlayerTracked(UUID playerUUID) {
        validatePlayerUUID(playerUUID);

        if (!isAvailable()) {
            return false;
        }

        PlaytimeCache cache = plugin.getPlaytimeCache();
        if (cache != null) {
            return cache.getCachedPlayers().contains(playerUUID);
        }

        return false;
    }

    @Override
    public CompletableFuture<Boolean> savePlayerData(UUID playerUUID) {
        validatePlayerUUID(playerUUID);

        if (!isAvailable()) {
            return CompletableFuture.completedFuture(false);
        }

        PlaytimeCache cache = plugin.getPlaytimeCache();
        if (cache == null || !cache.getCachedPlayers().contains(playerUUID)) {
            return CompletableFuture.completedFuture(false);
        }

        // Get player data from cache
        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null) {
            return CompletableFuture.completedFuture(false);
        }

        long totalPlaytime = cache.getTotalPlaytime(playerUUID);
        long currentTime = System.currentTimeMillis();

        return plugin.getDatabase().savePlayerData(
                playerUUID,
                player.getName(),
                totalPlaytime,
                currentTime, // Using current time as login time
                0L // Not logged out yet
        );
    }

    @Override
    public String getPluginVersion() {
        if (plugin == null) {
            return "Unknown";
        }

        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean isAvailable() {
        return plugin != null &&
                plugin.isEnabled() &&
                plugin.getConfigManager() != null &&
                plugin.getDatabase() != null &&
                plugin.getPlaytimeCache() != null;
    }

    /**
     * Formats playtime seconds into a human-readable string
     *
     * @param totalSeconds Total seconds to format
     * @param longFormat Whether to use long format
     * @return Formatted playtime string
     */
    private String formatPlaytime(long totalSeconds, boolean longFormat) {
        if (totalSeconds <= 0) {
            return longFormat ? "0d 0h 0m 0s" : "0h 0m";
        }

        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        String format = longFormat ?
                plugin.getConfigManager().getPlaytimeFormatLong() :
                plugin.getConfigManager().getPlaytimeFormatShort();

        // Replace placeholders
        format = format.replace("%playtime_total_days%", String.valueOf(days));
        format = format.replace("%playtime_total_hours%", String.valueOf(hours));
        format = format.replace("%playtime_total_minutes%", String.valueOf(minutes));
        format = format.replace("%playtime_total_seconds%", String.valueOf(seconds));
        format = format.replace("%playtime_total_seconds_raw%", String.valueOf(totalSeconds));

        return format;
    }

    /**
     * Validates that a player UUID is not null
     *
     * @param playerUUID UUID to validate
     * @throws IllegalArgumentException if UUID is null
     */
    private void validatePlayerUUID(UUID playerUUID) {
        if (playerUUID == null) {
            throw new IllegalArgumentException("Player UUID cannot be null");
        }
    }

    /**
     * Validates that seconds value is positive
     *
     * @param seconds Seconds to validate
     * @throws IllegalArgumentException if seconds is negative
     */
    private void validatePositiveSeconds(long seconds) {
        if (seconds < 0) {
            throw new IllegalArgumentException("Seconds must be positive, got: " + seconds);
        }
    }
}