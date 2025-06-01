package me.itzstanleex.hodinovac.util;

import me.itzstanleex.hodinovac.Hodinovac;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages all plugin messages with MiniMessage support and placeholder replacement.
 * All messages are configurable through config.yml and support reload functionality.
 *
 * @author ItzStanleex
 * @version 1.0.0
 */
public class MessageManager {

    private final Hodinovac plugin;
    private final MiniMessage miniMessage;

    // Cached messages for better performance
    private final Map<String, String> messageCache = new HashMap<>();

    /**
     * Constructor for MessageManager
     *
     * @param plugin Main plugin instance
     */
    public MessageManager(Hodinovac plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        loadMessages();
    }

    /**
     * Loads all messages from config into cache
     */
    public void loadMessages() {
        messageCache.clear();

        // Load all message paths from config
        var config = plugin.getConfigManager().getConfig();

        // Playtime messages
        messageCache.put("playtime.own", config.getString("messages.playtime.own", "<green>⏰ Your playtime: <bold>{playtime}</bold></green>"));
        messageCache.put("playtime.other", config.getString("messages.playtime.other", "<green>⏰ {player}'s playtime: <bold>{playtime}</bold></green>"));
        messageCache.put("playtime.session", config.getString("messages.playtime.session", "<aqua>📊 Session: {session} | Status: {status}</aqua>"));
        messageCache.put("playtime.added", config.getString("messages.playtime.added", "<green>✓ Added {time} to {player}'s playtime.</green>"));
        messageCache.put("playtime.removed", config.getString("messages.playtime.removed", "<green>✓ Removed {time} from {player}'s playtime.</green>"));

        // AFK messages
        messageCache.put("afk.went_afk", config.getString("messages.afk.went_afk", "<yellow>{player} is now AFK</yellow>"));
        messageCache.put("afk.returned", config.getString("messages.afk.returned", "<green>{player} is no longer AFK</green>"));

        // Error messages
        messageCache.put("errors.player_not_found", config.getString("messages.errors.player_not_found", "<red>✗ Player '{player}' not found.</red>"));
        messageCache.put("errors.invalid_time", config.getString("messages.errors.invalid_time", "<red>✗ Invalid time format. Use formats like: 1d, 2h, 30m, 1h30m</red>"));
        messageCache.put("errors.no_permission", config.getString("messages.errors.no_permission", "<red>✗ You don't have permission to do that.</red>"));
        messageCache.put("errors.console_player_required", config.getString("messages.errors.console_player_required", "<red>✗ Console must specify a player name.</red>"));
        messageCache.put("errors.operation_failed", config.getString("messages.errors.operation_failed", "<red>✗ Operation failed. Please try again.</red>"));

        // Reload messages
        messageCache.put("reload.success", config.getString("messages.reload.success", "<green>✓ Configuration reloaded successfully!</green>"));
        messageCache.put("reload.failed", config.getString("messages.reload.failed", "<red>✗ Failed to reload configuration. Check console for errors.</red>"));
        messageCache.put("reload.reloading", config.getString("messages.reload.reloading", "<yellow>Reloading Hodinovac configuration...</yellow>"));

        // Prefix
        messageCache.put("prefix", config.getString("messages.prefix", "<gradient:#00ff87:#60efff>[Hodinovac]</gradient> "));

        plugin.getDebugger().log("Loaded " + messageCache.size() + " messages from configuration");
    }

    /**
     * Gets a message from config with placeholder replacement
     *
     * @param key Message key (e.g., "playtime.own")
     * @param placeholders Map of placeholders to replace
     * @return Formatted message string
     */
    public String getMessage(String key, Map<String, String> placeholders) {
        String message = messageCache.getOrDefault(key, "<red>Missing message: " + key + "</red>");

        // Replace placeholders
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }

        return message;
    }

    /**
     * Gets a message from config without placeholders
     *
     * @param key Message key
     * @return Formatted message string
     */
    public String getMessage(String key) {
        return getMessage(key, null);
    }

    /**
     * Sends a message to a command sender with prefix
     *
     * @param sender Command sender
     * @param key Message key
     * @param placeholders Placeholders to replace
     */
    public void sendMessage(CommandSender sender, String key, Map<String, String> placeholders) {
        String prefix = getMessage("prefix");
        String message = getMessage(key, placeholders);
        Component component = miniMessage.deserialize(prefix + message);
        sender.sendMessage(component);
    }

    /**
     * Sends a message to a command sender with prefix
     *
     * @param sender Command sender
     * @param key Message key
     */
    public void sendMessage(CommandSender sender, String key) {
        sendMessage(sender, key, null);
    }

    /**
     * Sends a message to a command sender without prefix
     *
     * @param sender Command sender
     * @param key Message key
     * @param placeholders Placeholders to replace
     */
    public void sendMessageNoPrefix(CommandSender sender, String key, Map<String, String> placeholders) {
        String message = getMessage(key, placeholders);
        Component component = miniMessage.deserialize(message);
        sender.sendMessage(component);
    }

    /**
     * Sends a message to a command sender without prefix
     *
     * @param sender Command sender
     * @param key Message key
     */
    public void sendMessageNoPrefix(CommandSender sender, String key) {
        sendMessageNoPrefix(sender, key, null);
    }

    /**
     * Converts a message to Component for advanced usage
     *
     * @param key Message key
     * @param placeholders Placeholders to replace
     * @return Component for the message
     */
    public Component getComponent(String key, Map<String, String> placeholders) {
        String message = getMessage(key, placeholders);
        return miniMessage.deserialize(message);
    }

    /**
     * Converts a message to Component for advanced usage
     *
     * @param key Message key
     * @return Component for the message
     */
    public Component getComponent(String key) {
        return getComponent(key, null);
    }

    /**
     * Helper method to create a placeholder map
     *
     * @param keyValuePairs Key-value pairs (key1, value1, key2, value2, ...)
     * @return Map of placeholders
     */
    public static Map<String, String> placeholders(String... keyValuePairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            if (i + 1 < keyValuePairs.length) {
                map.put(keyValuePairs[i], keyValuePairs[i + 1]);
            }
        }
        return map;
    }
}