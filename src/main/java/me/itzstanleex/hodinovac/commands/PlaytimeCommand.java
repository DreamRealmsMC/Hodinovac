package me.itzstanleex.hodinovac.commands;

import me.itzstanleex.hodinovac.Hodinovac;
import me.itzstanleex.hodinovac.api.HodinovacAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
 * @author ItzStanleyX
 * @version 1.0.0
 * @since 1.0.0
 */
public class PlaytimeCommand {

    private final Hodinovac plugin;
    private final HodinovacAPI api;
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
        sender.sendMessage(Component.text("Reloading Hodinovac configuration...", NamedTextColor.YELLOW));

        boolean success = plugin.reloadPlugin();

        if (success) {
            sender.sendMessage(Component.text("✓ Configuration reloaded successfully!", NamedTextColor.GREEN));
            plugin.getDebugger().log("Configuration reloaded by " + sender.getName());
        } else {
            sender.sendMessage(Component.text("✗ Failed to reload configuration. Check console for errors.", NamedTextColor.RED));
        }
    }

    /**
     * Handles the add command
     */
    private void handleAdd(CommandSender sender, String playerName, String timeString) {
        // Parse time
        long seconds = parseTimeString(timeString);
        if (seconds <= 0) {
            sender.sendMessage(Component.text("✗ Invalid time format. Use formats like: 1d, 2h, 30m, 1h30m", NamedTextColor.RED));
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
                sender.sendMessage(Component.text("✗ Player '" + playerName + "' not found.", NamedTextColor.RED));
                return;
            }

            boolean success = api.addPlaytime(target.getUniqueId(), seconds);

            if (success) {
                String formattedTime = formatDuration(seconds);
                sender.sendMessage(Component.text("✓ Added " + formattedTime + " to " + target.getName() + "'s playtime.", NamedTextColor.GREEN));
                plugin.getDebugger().log(sender.getName() + " added " + seconds + " seconds to " + target.getName());
            } else {
                sender.sendMessage(Component.text("✗ Failed to add playtime. Player may not be tracked.", NamedTextColor.RED));
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
            sender.sendMessage(Component.text("✗ Invalid time format. Use formats like: 1d, 2h, 30m, 1h30m", NamedTextColor.RED));
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
                sender.sendMessage(Component.text("✗ Player '" + playerName + "' not found.", NamedTextColor.RED));
                return;
            }

            boolean success = api.removePlaytime(target.getUniqueId(), seconds);

            if (success) {
                String formattedTime = formatDuration(seconds);
                sender.sendMessage(Component.text("✓ Removed " + formattedTime + " from " + target.getName() + "'s playtime.", NamedTextColor.GREEN));
                plugin.getDebugger().log(sender.getName() + " removed " + seconds + " seconds from " + target.getName());
            } else {
                sender.sendMessage(Component.text("✗ Failed to remove playtime. Player may not be tracked.", NamedTextColor.RED));
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
                sender.sendMessage(Component.text("✗ Console must specify a player name.", NamedTextColor.RED));
                return;
            }

            target = (Player) sender;
            checkingSelf = true;

            if (!sender.hasPermission(PERMISSION_CHECK_SELF)) {
                sender.sendMessage(Component.text("✗ You don't have permission to check your own playtime.", NamedTextColor.RED));
                return;
            }
        } else {
            // Check other player
            if (!sender.hasPermission(PERMISSION_CHECK_OTHERS)) {
                sender.sendMessage(Component.text("✗ You don't have permission to check other players' playtime.", NamedTextColor.RED));
                return;
            }

            target = Bukkit.getOfflinePlayerIfCached(playerName);
            if (target == null) {
                target = Bukkit.getPlayerExact(playerName);
            }

            if (target == null || !target.hasPlayedBefore()) {
                sender.sendMessage(Component.text("✗ Player '" + playerName + "' not found.", NamedTextColor.RED));
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

            Component message = Component.text()
                    .append(Component.text("⏰ ", NamedTextColor.GOLD))
                    .append(Component.text(finalCheckingSelf ? "Your" : targetName + "'s", NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text(" playtime: ", NamedTextColor.GRAY))
                    .append(Component.text(formattedTime, NamedTextColor.GREEN, TextDecoration.BOLD))
                    .build();

            sender.sendMessage(message);

            // Show session playtime for online players
            if (finalTarget.isOnline() && api.isPlayerTracked(finalTarget.getUniqueId())) {
                long sessionSeconds = api.getSessionPlaytime(finalTarget.getUniqueId());
                String sessionFormatted = formatDuration(sessionSeconds);
                boolean isAfk = api.isAfk(finalTarget.getUniqueId());

                Component sessionMessage = Component.text()
                        .append(Component.text("📊 ", NamedTextColor.BLUE))
                        .append(Component.text("Session: ", NamedTextColor.GRAY))
                        .append(Component.text(sessionFormatted, NamedTextColor.AQUA))
                        .append(Component.text(" | Status: ", NamedTextColor.GRAY))
                        .append(Component.text(isAfk ? "AFK" : "Active", isAfk ? NamedTextColor.RED : NamedTextColor.GREEN))
                        .build();

                sender.sendMessage(sessionMessage);
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