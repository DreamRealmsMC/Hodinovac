package me.itzstanleex.hodinovac.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.itzstanleex.hodinovac.Hodinovac;
import me.itzstanleex.hodinovac.api.HodinovacAPI;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI integration for Hodinovac plugin.
 *
 * Provides placeholders for playtime data, AFK status, and formatted strings.
 * All placeholders are prefixed with the configured prefix (default: "hodinovac").
 *
 * Available placeholders:
 *
 * Total playtime placeholders:
 * - %hodinovac_total_days% - total full days played (excluding AFK)
 * - %hodinovac_total_hours% - hours remainder after days
 * - %hodinovac_total_minutes% - minutes remainder after minutes
 * - %hodinovac_total_seconds% - seconds remainder after minutes
 * - %hodinovac_total_seconds_raw% - total playtime in seconds (raw number)
 * - %hodinovac_total% - formatted total playtime (uses short format from config)
 * - %hodinovac_total_long% - formatted total playtime (uses long format from config)
 * - %hodinovac_total_better% - smart formatted total playtime (only shows non-zero values)
 *
 * Session playtime placeholders:
 * - %hodinovac_session_days% - session full days
 * - %hodinovac_session_hours% - session hours remainder
 * - %hodinovac_session_minutes% - session minutes remainder
 * - %hodinovac_session_seconds% - session seconds remainder
 * - %hodinovac_session_seconds_raw% - session playtime in seconds (raw number)
 * - %hodinovac_session% - formatted session playtime (uses short format from config)
 * - %hodinovac_session_long% - formatted session playtime (uses long format from config)
 * - %hodinovac_session_better% - smart formatted session playtime (only shows non-zero values)
 *
 * AFK status placeholders:
 * - %hodinovac_afk_status% - string from config (afk_format.afk if AFK, afk_format.not_afk if not AFK)
 * - %hodinovac_afk_boolean% - boolean true/false representing AFK state
 * - %hodinovac_time_since_move% - time since last move in seconds
 *
 * Better format examples:
 * - Player with 1d 5h 30m will show: "1d 5h 30m" (if all enabled)
 * - Player with 0d 0h 20m will show: "20m" (skips zero values)
 * - Player with 0d 2h 0m will show: "2h" (skips zero values)
 * - Configuration allows enabling/disabling specific units and customizing suffixes
 *
 * @author ItzStanleex
 * @version 1.0.0
 * @since 1.0.0
 */
public class PlaceholderHook extends PlaceholderExpansion {

    private final Hodinovac plugin;
    private final HodinovacAPI api;

    /**
     * Constructor for PlaceholderHook
     *
     * @param plugin Main plugin instance
     */
    public PlaceholderHook(Hodinovac plugin) {
        this.plugin = plugin;
        this.api = Hodinovac.getApi();
    }

    /**
     * Gets the plugin identifier for PlaceholderAPI
     *
     * @return Plugin identifier
     */
    @Override
    public @NotNull String getIdentifier() {
        return "hodinovac";
    }

    /**
     * Gets the plugin author
     *
     * @return Plugin author
     */
    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    /**
     * Gets the plugin version
     *
     * @return Plugin version
     */
    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /**
     * Indicates this expansion should persist through reloads
     *
     * @return true to persist
     */
    @Override
    public boolean persist() {
        return true;
    }

    /**
     * Indicates this expansion can be registered
     *
     * @return true pokud can register
     */
    @Override
    public boolean canRegister() {
        return plugin != null && plugin.isEnabled();
    }

    /**
     * Main placeholder processing method
     *
     * @param player Player requesting the placeholder
     * @param params Placeholder parameters (everything after the identifier)
     * @return Placeholder value or null if not found
     */
    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null || api == null || !api.isAvailable()) {
            return null;
        }

        try {
            return processPlaceholder(player, params.toLowerCase());
        } catch (Exception e) {
            plugin.getLogger().warning("Error processing placeholder '" + params + "' for player " +
                    player.getName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Processes individual placeholder requests
     *
     * @param player Player requesting the placeholder
     * @param params Lowercase placeholder parameters
     * @return Placeholder value or null if not found
     */
    private @Nullable String processPlaceholder(OfflinePlayer player, String params) {
        // Total playtime placeholders
        switch (params) {
            case "total_days":
                return String.valueOf(getTotalDays(player));
            case "total_hours":
                return String.valueOf(getTotalHours(player));
            case "total_minutes":
                return String.valueOf(getTotalMinutes(player));
            case "total_seconds":
                return String.valueOf(getTotalSeconds(player));
            case "total_seconds_raw":
                return String.valueOf(api.getPlaytime(player.getUniqueId()));
            case "total":
                return api.getFormattedPlaytime(player.getUniqueId(), false);
            case "total_long":
                return api.getFormattedPlaytime(player.getUniqueId(), true);

            // Session playtime placeholders
            case "session_days":
                return String.valueOf(getSessionDays(player));
            case "session_hours":
                return String.valueOf(getSessionHours(player));
            case "session_minutes":
                return String.valueOf(getSessionMinutes(player));
            case "session_seconds":
                return String.valueOf(getSessionSeconds(player));
            case "session_seconds_raw":
                return String.valueOf(api.getSessionPlaytime(player.getUniqueId()));
            case "session":
                return api.getFormattedSessionPlaytime(player.getUniqueId(), false);
            case "session_long":
                return api.getFormattedSessionPlaytime(player.getUniqueId(), true);

            // AFK status placeholders
            case "afk_status":
                return getAfkStatusString(player);
            case "afk_boolean":
                return String.valueOf(api.isAfk(player.getUniqueId()));
            case "time_since_move":
                return String.valueOf(getTimeSinceMove(player));

            // Additional utility placeholders
            case "is_tracked":
                return String.valueOf(api.isPlayerTracked(player.getUniqueId()));
            case "afk_timeout":
                return String.valueOf(api.getAfkTimeoutSeconds());

            // Better format placeholders
            case "total_better":
                return getBetterFormattedPlaytime(player, false);
            case "session_better":
                return getBetterFormattedPlaytime(player, true);

            default:
                return null;
        }
    }

    /**
     * Gets total days from player's playtime
     *
     * @param player Target player
     * @return Total days played
     */
    private long getTotalDays(OfflinePlayer player) {
        long totalSeconds = api.getPlaytime(player.getUniqueId());
        return totalSeconds / 86400; // 86400 seconds in a day
    }

    /**
     * Gets total hours remainder from player's playtime
     *
     * @param player Target player
     * @return Hours remainder after days
     */
    private long getTotalHours(OfflinePlayer player) {
        long totalSeconds = api.getPlaytime(player.getUniqueId());
        return (totalSeconds % 86400) / 3600; // Hours after removing full days
    }

    /**
     * Gets total minutes remainder from player's playtime
     *
     * @param player Target player
     * @return Minutes remainder after hours
     */
    private long getTotalMinutes(OfflinePlayer player) {
        long totalSeconds = api.getPlaytime(player.getUniqueId());
        return (totalSeconds % 3600) / 60; // Minutes after removing hours
    }

    /**
     * Gets total seconds remainder from player's playtime
     *
     * @param player Target player
     * @return Seconds remainder after minutes
     */
    private long getTotalSeconds(OfflinePlayer player) {
        long totalSeconds = api.getPlaytime(player.getUniqueId());
        return totalSeconds % 60; // Seconds after removing minutes
    }

    /**
     * Gets session days from player's current session
     *
     * @param player Target player
     * @return Session days
     */
    private long getSessionDays(OfflinePlayer player) {
        long sessionSeconds = api.getSessionPlaytime(player.getUniqueId());
        return sessionSeconds / 86400;
    }

    /**
     * Gets session hours remainder from player's current session
     *
     * @param player Target player
     * @return Session hours remainder
     */
    private long getSessionHours(OfflinePlayer player) {
        long sessionSeconds = api.getSessionPlaytime(player.getUniqueId());
        return (sessionSeconds % 86400) / 3600;
    }

    /**
     * Gets session minutes remainder from player's current session
     *
     * @param player Target player
     * @return Session minutes remainder
     */
    private long getSessionMinutes(OfflinePlayer player) {
        long sessionSeconds = api.getSessionPlaytime(player.getUniqueId());
        return (sessionSeconds % 3600) / 60;
    }

    /**
     * Gets session seconds remainder from player's current session
     *
     * @param player Target player
     * @return Session seconds remainder
     */
    private long getSessionSeconds(OfflinePlayer player) {
        long sessionSeconds = api.getSessionPlaytime(player.getUniqueId());
        return sessionSeconds % 60;
    }

    /**
     * Gets AFK status string from configuration
     *
     * @param player Target player
     * @return AFK status string from config
     */
    private String getAfkStatusString(OfflinePlayer player) {
        boolean isAfk = api.isAfk(player.getUniqueId());

        if (isAfk) {
            return plugin.getConfigManager().getAfkFormatAfk();
        } else {
            return plugin.getConfigManager().getAfkFormatNotAfk();
        }
    }

    /**
     * Gets time since player last moved in seconds
     *
     * @param player Target player
     * @return Time since last move in seconds, -1 if not tracked
     */
    private long getTimeSinceMove(OfflinePlayer player) {
        long timeSinceMove = api.getTimeSinceLastMove(player.getUniqueId());
        return timeSinceMove >= 0 ? timeSinceMove / 1000 : -1; // Convert to seconds
    }

    /**
     * Override for online players (provides better performance)
     *
     * @param player Online player requesting the placeholder
     * @param params Placeholder parameters
     * @return Placeholder value or null if not found
     */
    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        return onRequest(player, params);
    }
    /**
     * Formats playtime using better format configuration
     * Only shows non-zero values based on configuration
     *
     * @param player Target player
     * @param isSession true for session playtime, false for total playtime
     * @return Better formatted playtime string
     */
    private String getBetterFormattedPlaytime(OfflinePlayer player, boolean isSession) {
        long totalSeconds = isSession ?
                api.getSessionPlaytime(player.getUniqueId()) :
                api.getPlaytime(player.getUniqueId());

        return formatTimeBetter(totalSeconds);
    }

    /**
     * Formats time using better format configuration
     * Shows only non-zero values and enabled components
     *
     * @param totalSeconds Total seconds to format
     * @return Better formatted time string
     */
    private String formatTimeBetter(long totalSeconds) {
        if (totalSeconds <= 0) {
            return "0" + (plugin.getConfigManager().isBetterFormatMinutesEnabled() ?
                    plugin.getConfigManager().getBetterFormatMinutesSuffix() : "");
        }

        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder result = new StringBuilder();

        // Add days if enabled and greater than 0
        if (plugin.getConfigManager().isBetterFormatDaysEnabled() && days > 0) {
            result.append(days).append(plugin.getConfigManager().getBetterFormatDaysSuffix());
        }

        // Add hours if enabled and greater than 0
        if (plugin.getConfigManager().isBetterFormatHoursEnabled() && hours > 0) {
            if (result.length() > 0) result.append(" ");
            result.append(hours).append(plugin.getConfigManager().getBetterFormatHoursSuffix());
        }

        // Add minutes if enabled and greater than 0
        if (plugin.getConfigManager().isBetterFormatMinutesEnabled() && minutes > 0) {
            if (result.length() > 0) result.append(" ");
            result.append(minutes).append(plugin.getConfigManager().getBetterFormatMinutesSuffix());
        }

        // Add seconds if enabled and greater than 0
        if (plugin.getConfigManager().isBetterFormatSecondsEnabled() && seconds > 0) {
            if (result.length() > 0) result.append(" ");
            result.append(seconds).append(plugin.getConfigManager().getBetterFormatSecondsSuffix());
        }

        // If result is empty (all values are 0 or disabled), show at least something
        if (result.length() == 0) {
            // Show the smallest enabled unit with 0
            if (plugin.getConfigManager().isBetterFormatSecondsEnabled()) {
                result.append("0").append(plugin.getConfigManager().getBetterFormatSecondsSuffix());
            } else if (plugin.getConfigManager().isBetterFormatMinutesEnabled()) {
                result.append("0").append(plugin.getConfigManager().getBetterFormatMinutesSuffix());
            } else if (plugin.getConfigManager().isBetterFormatHoursEnabled()) {
                result.append("0").append(plugin.getConfigManager().getBetterFormatHoursSuffix());
            } else if (plugin.getConfigManager().isBetterFormatDaysEnabled()) {
                result.append("0").append(plugin.getConfigManager().getBetterFormatDaysSuffix());
            } else {
                result.append("0m"); // fallback
            }
        }

        return result.toString();
    }
}