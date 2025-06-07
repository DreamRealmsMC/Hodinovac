package me.itzstanleex.hodinovac.config;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import lombok.Getter;
import me.itzstanleex.hodinovac.Hodinovac;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

/**
 * Configuration manager for Hodinovac plugin using BoostedYAML.
 *
 * Handles loading, reloading, and accessing configuration values with type safety
 * and default value fallbacks. Supports automatic config updates and validation.
 *
 * @author ItzStanleex
 * @version 1.0.0
 */
public class ConfigManager {

    private final Hodinovac plugin;

    @Getter
    private YamlDocument config;

    /**
     * Constructor for ConfigManager
     *
     * @param plugin Main plugin instance
     */
    public ConfigManager(Hodinovac plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads or creates the configuration file
     *
     * @return true if configuration was loaded successfully, false otherwise
     */
    public boolean loadConfig() {
        try {

            this.config = YamlDocument.create(
                    new File(plugin.getDataFolder(), "config.yml"),
                    Objects.requireNonNull(plugin.getResource("config.yml")),
                    GeneralSettings.DEFAULT,
                    LoaderSettings.builder().setAutoUpdate(true).build(),
                    DumperSettings.DEFAULT,
                    UpdaterSettings.builder().setVersioning(new BasicVersioning("config-version")).build()
            );

            // Validate configuration
            if (!validateConfig()) {
                return false;
            }

            return true;

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    /**
     * Reloads the configuration file
     *
     * @return true if configuration was reloaded successfully, false otherwise
     */
    public boolean reloadConfig() {
        try {
            plugin.getDebugger().log("Reloading configuration file...");

            config.reload();

            if (!validateConfig()) {
                plugin.getLogger().severe("Configuration validation failed after reload!");
                return false;
            }

            plugin.getDebugger().log("Configuration reloaded successfully");
            return true;

        } catch (IOException e) {
            plugin.getLogger().severe("Failed to reload configuration: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Validates the configuration for required fields and correct types
     *
     * @return true if configuration is valid, false otherwise
     */
    private boolean validateConfig() {
        try {
            // Validate MySQL settings
            if (config.getString("mysql.host", "").isEmpty()) {
                plugin.getLogger().severe("MySQL host is not configured!");
                return false;
            }

            if (config.getInt("mysql.port", 0) <= 0 || config.getInt("mysql.port", 0) > 65535) {
                plugin.getLogger().severe("MySQL port is invalid!");
                return false;
            }

            if (config.getString("mysql.database", "").isEmpty()) {
                plugin.getLogger().severe("MySQL database name is not configured!");
                return false;
            }

            if (config.getString("mysql.user", "").isEmpty()) {
                plugin.getLogger().severe("MySQL user is not configured!");
                return false;
            }

            // Validate format strings are not empty
            if (config.getString("playtime_format_short", "").isEmpty()) {
                plugin.getLogger().severe("Short playtime format is empty!");
                return false;
            }

            if (config.getString("playtime_format_long", "").isEmpty()) {
                plugin.getLogger().severe("Long playtime format is empty!");
                return false;
            }

            plugin.getDebugger().log("Configuration validation passed");
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Configuration validation error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // MySQL Configuration Methods

    /**
     * Gets MySQL host from configuration
     *
     * @return MySQL host address
     */
    public String getMySQLHost() {
        return config.getString("mysql.host", "localhost");
    }

    /**
     * Gets MySQL port from configuration
     *
     * @return MySQL port number
     */
    public int getMySQLPort() {
        return config.getInt("mysql.port", 3306);
    }

    /**
     * Gets MySQL database name from configuration
     *
     * @return MySQL database name
     */
    public String getMySQLDatabase() {
        return config.getString("mysql.database", "hodinovac");
    }

    /**
     * Gets MySQL username from configuration
     *
     * @return MySQL username
     */
    public String getMySQLUser() {
        return config.getString("mysql.user", "root");
    }

    /**
     * Gets MySQL password from configuration
     *
     * @return MySQL password
     */
    public String getMySQLPassword() {
        return config.getString("mysql.password", "");
    }

    // Format Configuration Methods

    /**
     * Gets short playtime format string from configuration
     *
     * @return Short format string for playtime display
     */
    public String getPlaytimeFormatShort() {
        return config.getString("playtime_format_short", "%playtime_total_hours%h %playtime_total_minutes%m");
    }

    /**
     * Gets long playtime format string from configuration
     *
     * @return Long format string for playtime display
     */
    public String getPlaytimeFormatLong() {
        return config.getString("playtime_format_long",
                "%playtime_total_days%d %playtime_total_hours%h %playtime_total_minutes%m %playtime_total_seconds%s");
    }

    /**
     * Gets AFK status string from configuration
     *
     * @return String to display when player is AFK
     */
    public String getAfkFormatAfk() {
        return config.getString("afk_format.afk", "AFK");
    }

    /**
     * Gets non-AFK status string from configuration
     *
     * @return String to display when player is not AFK
     */
    public String getAfkFormatNotAfk() {
        return config.getString("afk_format.not_afk", "");
    }

    // Debug Configuration

    /**
     * Checks if debug mode is enabled
     *
     * @return true if debug mode is enabled, false otherwise
     */
    public boolean isDebugEnabled() {
        return config.getBoolean("debug", false);
    }

    // AFK Configuration

    /**
     * Gets AFK timeout in seconds (how long without movement before marked AFK)
     *
     * @return AFK timeout in seconds
     */
    public long getAfkTimeoutSeconds() {
        return config.getLong("afk.timeout_seconds", 300L); // 5 minutes default
    }

    /**
     * Gets playtime update interval in seconds
     *
     * @return Update interval for playtime cache sync to database
     */
    public long getPlaytimeUpdateInterval() {
        return config.getLong("playtime_update_interval", 60L); // 1 minute default
    }

    /**
     * Gets the complete MySQL connection URL
     *
     * @return Formatted MySQL JDBC connection URL
     */
    public String getMySQLUrl() {
        return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&autoReconnect=true",
                getMySQLHost(), getMySQLPort(), getMySQLDatabase());
    }

    /**
     * Saves the current configuration to file
     *
     * @return true if save was successful, false otherwise
     */
    public boolean saveConfig() {
        try {
            config.save();
            plugin.getDebugger().log("Configuration saved successfully");
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save configuration: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    // Better Format Configuration Methods

    /**
     * Checks if days are enabled in better format
     *
     * @return true if days should be displayed
     */
    public boolean isBetterFormatDaysEnabled() {
        return config.getBoolean("playtime_better_format.days.enabled", true);
    }

    /**
     * Gets days suffix for better format
     *
     * @return suffix for days
     */
    public String getBetterFormatDaysSuffix() {
        return config.getString("playtime_better_format.days.suffix", "d");
    }

    /**
     * Checks if hours are enabled in better format
     *
     * @return true if hours should be displayed
     */
    public boolean isBetterFormatHoursEnabled() {
        return config.getBoolean("playtime_better_format.hours.enabled", true);
    }

    /**
     * Gets hours suffix for better format
     *
     * @return suffix for hours
     */
    public String getBetterFormatHoursSuffix() {
        return config.getString("playtime_better_format.hours.suffix", "h");
    }

    /**
     * Checks if minutes are enabled in better format
     *
     * @return true if minutes should be displayed
     */
    public boolean isBetterFormatMinutesEnabled() {
        return config.getBoolean("playtime_better_format.minutes.enabled", true);
    }

    /**
     * Gets minutes suffix for better format
     *
     * @return suffix for minutes
     */
    public String getBetterFormatMinutesSuffix() {
        return config.getString("playtime_better_format.minutes.suffix", "m");
    }

    /**
     * Checks if seconds are enabled in better format
     *
     * @return true if seconds should be displayed
     */
    public boolean isBetterFormatSecondsEnabled() {
        return config.getBoolean("playtime_better_format.seconds.enabled", false);
    }

    /**
     * Gets seconds suffix for better format
     *
     * @return suffix for seconds
     */
    public String getBetterFormatSecondsSuffix() {
        return config.getString("playtime_better_format.seconds.suffix", "s");
    }

    /**
     * Gets the placeholder prefix from configuration
     *
     * @return placeholder prefix
     */
    public String getPlaceholderPrefix() {
        return config.getString("placeholders.prefix", "hodinovac");
    }
}