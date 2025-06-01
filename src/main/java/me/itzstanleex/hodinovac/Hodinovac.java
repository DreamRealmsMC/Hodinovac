package me.itzstanleex.hodinovac;

import lombok.Getter;
import me.itzstanleex.hodinovac.afk.AfkManager;
import me.itzstanleex.hodinovac.api.HodinovacAPI;
import me.itzstanleex.hodinovac.api.HodinovacAPIImpl;
import me.itzstanleex.hodinovac.cache.PlaytimeCache;
import me.itzstanleex.hodinovac.commands.PlaytimeCommand;
import me.itzstanleex.hodinovac.config.ConfigManager;
import me.itzstanleex.hodinovac.database.MySQLDatabase;
import me.itzstanleex.hodinovac.placeholder.PlaceholderHook;
import me.itzstanleex.hodinovac.util.Debugger;
import me.itzstanleex.hodinovac.util.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main plugin class for Hodinovac - A playtime tracking plugin for Minecraft Paper servers.
 *
 * This plugin tracks player playtime excluding AFK time, stores data in MySQL database,
 * provides PlaceholderAPI integration, and offers a comprehensive API for other plugins.
 *
 * @author ItzStanleex
 * @version 1.0.0
 */
public final class Hodinovac extends JavaPlugin {

    @Getter
    private static Hodinovac instance;

    @Getter
    private ConfigManager configManager;

    @Getter
    private MySQLDatabase database;

    @Getter
    private PlaytimeCache playtimeCache;

    @Getter
    private AfkManager afkManager;

    @Getter
    private HodinovacAPI api;

    @Getter
    private Debugger debugger;

    @Getter
    private MessageManager messageManager;

    @Getter
    private ExecutorService executorService;

    private PlaceholderHook placeholderHook;
    private PlaytimeCommand playtimeCommand;

    /**
     * Plugin startup method - initializes all components in correct order
     */
    @Override
    public void onEnable() {
        instance = this;

        // Initialize thread pool for async operations
        this.executorService = Executors.newFixedThreadPool(3, r -> {
            Thread thread = new Thread(r, "Hodinovac-AsyncPool");
            thread.setDaemon(true);
            return thread;
        });

        // Initialize debugger first (needed by other components)
        this.debugger = new Debugger(this);
        debugger.log("Starting Hodinovac plugin initialization...");

        // Load configuration
        this.configManager = new ConfigManager(this);
        if (!configManager.loadConfig()) {
            getLogger().severe("Failed to load configuration! Disabling plugin...");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize message manager after config is loaded
        this.messageManager = new MessageManager(this);

        // Initialize database connection
        this.database = new MySQLDatabase(this);
        CompletableFuture<Boolean> dbInit = database.initialize();

        // Wait for database initialization before proceeding
        dbInit.thenAccept(success -> {
            if (!success) {
                getLogger().severe("Failed to initialize database! Disabling plugin...");
                Bukkit.getServer().getScheduler().runTask(this, () -> {
                    Bukkit.getPluginManager().disablePlugin(this);
                });
                return;
            }

            // Continue initialization on main thread
            Bukkit.getServer().getScheduler().runTask(this, this::continueInitialization);
        }).exceptionally(throwable -> {
            getLogger().severe("Database initialization failed with exception: " + throwable.getMessage());
            throwable.printStackTrace();
            Bukkit.getServer().getScheduler().runTask(this, () -> {
                Bukkit.getPluginManager().disablePlugin(this);
            });
            return null;
        });
    }

    /**
     * Continues plugin initialization after database is ready
     */
    private void continueInitialization() {
        try {
            debugger.log("Database initialized successfully, continuing with plugin startup...");

            // Initialize cache manager
            this.playtimeCache = new PlaytimeCache(this);

            // Initialize AFK manager
            this.afkManager = new AfkManager(this);

            // Initialize API implementation
            this.api = new HodinovacAPIImpl(this);

            // Register PlaceholderAPI hook if available
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                this.placeholderHook = new PlaceholderHook(this);
                placeholderHook.register();
                debugger.log("PlaceholderAPI hook registered successfully");
            } else {
                getLogger().warning("PlaceholderAPI not found! Placeholder functionality will be disabled.");
            }

            // Initialize and register commands
            this.playtimeCommand = new PlaytimeCommand(this);

            // Start cache sync task
            playtimeCache.startSyncTask();

            debugger.log("Hodinovac plugin enabled successfully!");
            getLogger().info("Hodinovac v" + getDescription().getVersion() + " has been enabled!");

        } catch (Exception e) {
            getLogger().severe("Failed to initialize plugin components: " + e.getMessage());
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    /**
     * Plugin shutdown method - cleans up resources and saves data
     */
    @Override
    public void onDisable() {
        debugger.log("Shutting down Hodinovac plugin...");

        // Unregister PlaceholderAPI hook
        if (placeholderHook != null) {
            placeholderHook.unregister();
        }

        // Save all cached data to database
        if (playtimeCache != null) {
            playtimeCache.shutdown();
        }

        // Close database connections
        if (database != null) {
            database.close();
        }

        // Shutdown executor service
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        debugger.log("Hodinovac plugin disabled successfully!");
        getLogger().info("Hodinovac has been disabled!");
    }

    /**
     * Reloads the plugin configuration and refreshes all components
     *
     * @return true if reload was successful, false otherwise
     */
    public boolean reloadPlugin() {
        try {
            debugger.log("Reloading Hodinovac configuration...");

            // Reload configuration
            if (!configManager.reloadConfig()) {
                return false;
            }

            // Reload messages after config reload
            if (messageManager != null) {
                messageManager.loadMessages();
            }

            // Refresh components that depend on config
            if (database != null) {
                database.refreshConfig();
            }

            if (afkManager != null) {
                afkManager.refreshConfig();
            }

            debugger.log("Hodinovac configuration reloaded successfully!");
            return true;

        } catch (Exception e) {
            getLogger().severe("Failed to reload configuration: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Gets the plugin's API instance
     *
     * @return HodinovacAPI instance for external plugin integration
     */
    public static HodinovacAPI getApi() {
        return instance != null ? instance.api : null;
    }
}