package me.itzstanleex.hodinovac.commands;

import me.itzstanleex.hodinovac.Hodinovac;
import me.itzstanleex.hodinovac.api.HodinovacAPI;
import me.itzstanleex.hodinovac.util.MessageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.bukkit.CloudBukkitCapabilities;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.LegacyPaperCommandManager;
import org.incendo.cloud.parser.standard.StringParser;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Command implementation for Hodinovac plugin using Incendo Cloud Command Framework.
 *
 * Provides the following commands:
 * - /playtime reload - Reloads plugin configuration
 * - /playtime add <player> <time> - Adds playtime to a player
 * - /playtime remove <player> <time> - Removes playtime from a player
 * - /playtime check [player] - Checks a player's total playtime
 *
 * Time format examples: 1d, 2h, 30m, 45s, 1h30m, 2d5h30m
 *
 * All messages are now configurable through config.yml and support MiniMessage formatting.
 *
 * @author ItzStanleyX
 * @version 1.0.0
 * @since 1.0.0
 */
public class PlaytimeCommand {

    private final Hodinovac plugin;
    private final HodinovacAPI api;
    private final MessageManager messageManager;
    private final CommandManager<CommandSender> commandManager;

    // Pattern for parsing time strings like "1d2h30m45s"
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "(?:(?<days>\\d+)d)?(?:(?<hours>\\d+)h)?(?:(?<minutes>\\d+)m)?(?:(?<seconds>\\d+)s)?"
    );

    // Permission constants
    private static final String PERMISSION_RELOAD = "hodinovac.reload";
    private static final String PERMISSION_ADD = "hodinovac.add";
    private static final String PERMISSION_REMOVE = "hodinovac.remove";
    private static final String PERMISSION_CHECK_OTHERS = "hodinovac.check.others";
    private static final String PERMISSION_CHECK_SELF = "hodinovac.check.self";

    /**
     * Constructor for PlaytimeCommand
     *
     * @param plugin Main plugin instance
     */
    public PlaytimeCommand(Hodinovac plugin) {
        this.plugin = plugin;
        this.api = Hodinovac.getApi();
        this.messageManager = plugin.getMessageManager();

        try {
            this.commandManager = createCommandManager();
            registerCommands();
            plugin.getDebugger().log("Commands registered successfully using Incendo Cloud Command Framework");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize command manager: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Command initialization failed", e);
        }
    }

    /**
     * Creates and configures the command manager
     *
     * @return Configured command manager
     * @throws Exception if initialization fails
     */
    private CommandManager<CommandSender> createCommandManager() throws Exception {
        LegacyPaperCommandManager<CommandSender> manager = LegacyPaperCommandManager.createNative(
                plugin,
                ExecutionCoordinator.simpleCoordinator()
        );

        // Only register asynchronous completions, skip Brigadier for now
        if (manager.hasCapability(CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION)) {
            manager.registerAsynchronousCompletions();
        }

        return manager;
    }

    /**
     * Registers all plugin commands
     */
    private void registerCommands() {
        var playtimeCommand = commandManager.commandBuilder("playtime")
                .permission(PERMISSION_CHECK_SELF);

        // /playtime reload
        commandManager.command(playtimeCommand
                .literal("reload")
                .permission(PERMISSION_RELOAD)
                .handler(context -> handleReload(context.sender())));

        // /playtime add <player> <time>
        commandManager.command(playtimeCommand
                .literal("add")
                .permission(PERMISSION_ADD)
                .required("player", StringParser.stringParser())
                .required("time", StringParser.stringParser())
                .handler(context -> handleAdd(context.sender(), context.get("player"), context.get("time"))));

        // /playtime remove <player> <time>
        commandManager.command(playtimeCommand
                .literal("remove")
                .permission(PERMISSION_REMOVE)
                .required("player", StringParser.stringParser())
                .required("time", StringParser.stringParser())
                .handler(context -> handleRemove(context.sender(), context.get("player"), context.get("time"))));

        // /playtime check [player]
        commandManager.command(playtimeCommand
                .literal("check")
                .optional("player", StringParser.stringParser())
                .handler(context -> {
                    String playerName = (String) context.optional("player").orElse(null);
                    handleCheck(context.sender(), playerName);
                }));

        // Base /playtime command (show own playtime)
        commandManager.command(playtimeCommand
                .handler(context -> handleCheck(context.sender(), null)));
    }

    /**
     * Handles the reload command
     */
    private void handleReload(CommandSender sender) {
        messageManager.sendMessage(sender, "reload.reloading");

        boolean success = plugin.reloadPlugin();

        if (success) {
            messageManager.sendMessage(sender, "reload.success");
            plugin.getDebugger().log("Configuration reloaded by " + sender.getName());
        } else {
            messageManager.sendMessage(sender, "reload.failed");
        }
    }

    /**
     * Handles the add command
     */
    private void handleAdd(CommandSender sender, String playerName, String timeString) {
        // Parse time
        long seconds = parseTimeString(timeString);
        if (seconds <= 0) {
            messageManager.sendMessage(sender, "errors.invalid_time");
            return;
        }

        // Find player
        CompletableFuture.supplyAsync(() -> {
            OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(playerName);
            if (target == null) {
                // Try online players
                target = Bukkit.getPlayerExact(playerName);
            }
            return target;
        }).thenAccept(target -> {
            if (target == null || !target.hasPlayedBefore()) {
                messageManager.sendMessage(sender, "errors.player_not_found",
                        MessageManager.placeholders("player", playerName));
                return;
            }

            boolean success = api.addPlaytime(target.getUniqueId(), seconds);

            if (success) {
                String formattedTime = formatDuration(seconds);
                messageManager.sendMessage(sender, "playtime.added",
                        MessageManager.placeholders(
                                "time", formattedTime,
                                "player", target.getName() != null ? target.getName() : playerName
                        ));
                plugin.getDebugger().log(sender.getName() + " added " + seconds + " seconds to " + target.getName());
            } else {
                messageManager.sendMessage(sender, "errors.operation_failed");
            }
        });
    }

    /**
     * Handles the remove command
     */
    private void handleRemove(CommandSender sender, String playerName, String timeString) {
        // Parse time
        long seconds = parseTimeString(timeString);
        if (seconds <= 0) {
            messageManager.sendMessage(sender, "errors.invalid_time");
            return;
        }

        // Find player
        CompletableFuture.supplyAsync(() -> {
            OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(playerName);
            if (target == null) {
                target = Bukkit.getPlayerExact(playerName);
            }
            return target;
        }).thenAccept(target -> {
            if (target == null || !target.hasPlayedBefore()) {
                messageManager.sendMessage(sender, "errors.player_not_found",
                        MessageManager.placeholders("player", playerName));
                return;
            }

            boolean success = api.removePlaytime(target.getUniqueId(), seconds);

            if (success) {
                String formattedTime = formatDuration(seconds);
                messageManager.sendMessage(sender, "playtime.removed",
                        MessageManager.placeholders(
                                "time", formattedTime,
                                "player", target.getName() != null ? target.getName() : playerName
                        ));
                plugin.getDebugger().log(sender.getName() + " removed " + seconds + " seconds from " + target.getName());
            } else {
                messageManager.sendMessage(sender, "errors.operation_failed");
            }
        });
    }

    /**
     * Handles the check command
     */
    private void handleCheck(CommandSender sender, String playerName) {
        // Determine target player
        OfflinePlayer target;
        boolean checkingSelf = false;

        if (playerName == null) {
            // Check self
            if (!(sender instanceof Player)) {
                messageManager.sendMessage(sender, "errors.console_player_required");
                return;
            }

            target = (Player) sender;
            checkingSelf = true;

            if (!sender.hasPermission(PERMISSION_CHECK_SELF)) {
                messageManager.sendMessage(sender, "errors.no_permission");
                return;
            }
        } else {
            // Check other player
            if (!sender.hasPermission(PERMISSION_CHECK_OTHERS)) {
                messageManager.sendMessage(sender, "errors.no_permission");
                return;
            }

            target = Bukkit.getOfflinePlayerIfCached(playerName);
            if (target == null) {
                target = Bukkit.getPlayerExact(playerName);
            }

            if (target == null || !target.hasPlayedBefore()) {
                messageManager.sendMessage(sender, "errors.player_not_found",
                        MessageManager.placeholders("player", playerName));
                return;
            }
        }

        // Make variables effectively final for lambda
        final OfflinePlayer finalTarget = target;
        final boolean finalCheckingSelf = checkingSelf;

        // Get playtime asynchronously
        api.getPlaytimeAsync(finalTarget.getUniqueId()).thenAccept(totalSeconds -> {
            String targetName = finalTarget.getName() != null ? finalTarget.getName() : "Unknown";
            String formattedTime = api.getFormattedPlaytime(finalTarget.getUniqueId(), true);

            // Send main playtime message
            if (finalCheckingSelf) {
                messageManager.sendMessageNoPrefix(sender, "playtime.own",
                        MessageManager.placeholders("playtime", formattedTime));
            } else {
                messageManager.sendMessageNoPrefix(sender, "playtime.other",
                        MessageManager.placeholders(
                                "player", targetName,
                                "playtime", formattedTime
                        ));
            }

            // Show session playtime for online players
            if (finalTarget.isOnline() && api.isPlayerTracked(finalTarget.getUniqueId())) {
                long sessionSeconds = api.getSessionPlaytime(finalTarget.getUniqueId());
                String sessionFormatted = formatDuration(sessionSeconds);
                boolean isAfk = api.isAfk(finalTarget.getUniqueId());

                String afkStatus = isAfk ?
                        plugin.getConfigManager().getAfkFormatAfk() :
                        plugin.getConfigManager().getAfkFormatNotAfk();

                if (afkStatus.isEmpty()) {
                    afkStatus = isAfk ? "AFK" : "Active";
                }

                messageManager.sendMessageNoPrefix(sender, "playtime.session",
                        MessageManager.placeholders(
                                "session", sessionFormatted,
                                "status", afkStatus
                        ));
            }

            plugin.getDebugger().log(sender.getName() + " checked playtime for " + targetName);
        });
    }

    /**
     * Parses a time string into seconds
     *
     * @param timeString Time string (e.g., "1d2h30m45s")
     * @return Total seconds, or -1 if invalid
     */
    private long parseTimeString(String timeString) {
        if (timeString == null || timeString.trim().isEmpty()) {
            return -1;
        }

        Matcher matcher = TIME_PATTERN.matcher(timeString.toLowerCase().trim());
        if (!matcher.matches()) {
            return -1;
        }

        long totalSeconds = 0;

        String days = matcher.group("days");
        String hours = matcher.group("hours");
        String minutes = matcher.group("minutes");
        String seconds = matcher.group("seconds");

        try {
            if (days != null) {
                totalSeconds += Long.parseLong(days) * 86400; // 24 * 60 * 60
            }
            if (hours != null) {
                totalSeconds += Long.parseLong(hours) * 3600; // 60 * 60
            }
            if (minutes != null) {
                totalSeconds += Long.parseLong(minutes) * 60;
            }
            if (seconds != null) {
                totalSeconds += Long.parseLong(seconds);
            }
        } catch (NumberFormatException e) {
            return -1;
        }

        // Ensure at least something was parsed
        if (totalSeconds == 0 && (days != null || hours != null || minutes != null || seconds != null)) {
            return 0; // Valid but zero
        }

        return totalSeconds > 0 ? totalSeconds : -1;
    }

    /**
     * Formats duration in seconds to human-readable string
     *
     * @param seconds Duration in seconds
     * @return Formatted duration string
     */
    private String formatDuration(long seconds) {
        if (seconds <= 0) {
            return "0s";
        }

        Duration duration = Duration.ofSeconds(seconds);
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        long secs = duration.toSecondsPart();

        StringBuilder sb = new StringBuilder();

        if (days > 0) {
            sb.append(days).append("d");
        }
        if (hours > 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(hours).append("h");
        }
        if (minutes > 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(minutes).append("m");
        }
        if (secs > 0 || sb.length() == 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(secs).append("s");
        }

        return sb.toString();
    }
}