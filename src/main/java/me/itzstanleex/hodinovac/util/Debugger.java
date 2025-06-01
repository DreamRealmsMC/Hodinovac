package me.itzstanleex.hodinovac.util;

import me.itzstanleex.hodinovac.Hodinovac;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Thread-safe debug logging utility for Hodinovac plugin.
 *
 * Provides conditional debug logging based on configuration settings.
 * All logging operations are thread-safe and non-blocking to prevent
 * performance impact on the main server thread.
 *
 * Features:
 * - Thread-safe logging operations
 * - Conditional logging based on config
 * - Formatted timestamps
 * - Performance metrics logging
 * - Memory-efficient message queuing
 * - Multiple log levels support
 *
 * @author ItzStanleyX
 * @version 1.0.0
 * @since 1.0.0
 */
public class Debugger {

    private final Hodinovac plugin;
    private final AtomicBoolean debugEnabled;
    private final ConcurrentLinkedQueue<LogEntry> logQueue;
    private final DateTimeFormatter timeFormatter;

    // Performance tracking
    private volatile long lastPerformanceLog = 0;
    private static final long PERFORMANCE_LOG_INTERVAL = 60000; // 1 minute

    // Log level constants
    public static final int LEVEL_TRACE = 0;
    public static final int LEVEL_DEBUG = 1;
    public static final int LEVEL_INFO = 2;
    public static final int LEVEL_WARN = 3;
    public static final int LEVEL_ERROR = 4;

    /**
     * Constructor for Debugger
     *
     * @param plugin Main plugin instance
     */
    public Debugger(Hodinovac plugin) {
        this.plugin = plugin;
        this.debugEnabled = new AtomicBoolean(false);
        this.logQueue = new ConcurrentLinkedQueue<>();
        this.timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

        // Initialize debug state
        updateDebugState();
    }

    /**
     * Updates debug state from configuration
     */
    public void updateDebugState() {
        if (plugin.getConfigManager() != null) {
            debugEnabled.set(plugin.getConfigManager().isDebugEnabled());
        }
    }

    /**
     * Logs a debug message if debug mode is enabled
     *
     * @param message Debug message to log
     */
    public void log(String message) {
        log(LEVEL_DEBUG, message);
    }

    /**
     * Logs a debug message with custom level
     *
     * @param level Log level (use LEVEL_* constants)
     * @param message Debug message to log
     */
    public void log(int level, String message) {
        if (!debugEnabled.get()) {
            return;
        }

        try {
            String timestamp = LocalDateTime.now().format(timeFormatter);
            String levelString = getLevelString(level);
            String formattedMessage = String.format("[%s] [%s] %s", timestamp, levelString, message);

            // Log immediately for important messages
            if (level >= LEVEL_WARN) {
                plugin.getLogger().info("[DEBUG] " + formattedMessage);
            } else {
                // Queue less important messages
                logQueue.offer(new LogEntry(System.currentTimeMillis(), level, formattedMessage));
                processLogQueue();
            }

        } catch (Exception e) {
            // Fail silently to prevent debug logging from causing issues
            plugin.getLogger().warning("Debug logging failed: " + e.getMessage());
        }
    }

    /**
     * Logs a trace message (lowest level)
     *
     * @param message Trace message
     */
    public void trace(String message) {
        log(LEVEL_TRACE, message);
    }

    /**
     * Logs an info message
     *
     * @param message Info message
     */
    public void info(String message) {
        log(LEVEL_INFO, message);
    }

    /**
     * Logs a warning message
     *
     * @param message Warning message
     */
    public void warn(String message) {
        log(LEVEL_WARN, message);
    }

    /**
     * Logs an error message
     *
     * @param message Error message
     */
    public void error(String message) {
        log(LEVEL_ERROR, message);
    }

    /**
     * Logs an error message with exception details
     *
     * @param message Error message
     * @param throwable Exception to log
     */
    public void error(String message, Throwable throwable) {
        if (!debugEnabled.get()) {
            return;
        }

        error(message + ": " + throwable.getMessage());

        // Log stack trace for debugging
        for (StackTraceElement element : throwable.getStackTrace()) {
            trace("  at " + element.toString());
        }
    }

    /**
     * Logs performance metrics for a timed operation
     *
     * @param operation Operation name
     * @param startTime Start time in milliseconds
     */
    public void logPerformance(String operation, long startTime) {
        if (!debugEnabled.get()) {
            return;
        }

        long duration = System.currentTimeMillis() - startTime;
        log(LEVEL_INFO, "Performance: " + operation + " took " + duration + "ms");
    }

    /**
     * Logs database operation metrics
     *
     * @param operation Database operation name
     * @param recordCount Number of records affected
     * @param duration Duration in milliseconds
     */
    public void logDatabaseOperation(String operation, int recordCount, long duration) {
        if (!debugEnabled.get()) {
            return;
        }

        log(LEVEL_INFO, String.format("DB: %s (%d records) completed in %dms",
                operation, recordCount, duration));
    }

    /**
     * Logs memory usage information
     */
    public void logMemoryUsage() {
        if (!debugEnabled.get()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastPerformanceLog < PERFORMANCE_LOG_INTERVAL) {
            return;
        }

        lastPerformanceLog = currentTime;

        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();

        log(LEVEL_INFO, String.format("Memory: Used=%dMB, Free=%dMB, Total=%dMB, Max=%dMB",
                usedMemory / 1024 / 1024,
                freeMemory / 1024 / 1024,
                totalMemory / 1024 / 1024,
                maxMemory / 1024 / 1024));
    }

    /**
     * Logs plugin component initialization
     *
     * @param componentName Name of the component
     * @param success Whether initialization was successful
     * @param duration Duration in milliseconds
     */
    public void logComponentInit(String componentName, boolean success, long duration) {
        if (!debugEnabled.get()) {
            return;
        }

        String status = success ? "✓" : "✗";
        log(LEVEL_INFO, String.format("Component Init: %s %s (%dms)",
                status, componentName, duration));
    }

    /**
     * Logs cache statistics
     *
     * @param cacheType Type of cache
     * @param size Current cache size
     * @param hits Cache hits
     * @param misses Cache misses
     */
    public void logCacheStats(String cacheType, int size, long hits, long misses) {
        if (!debugEnabled.get()) {
            return;
        }

        long total = hits + misses;
        double hitRate = total > 0 ? (double) hits / total * 100 : 0;

        log(LEVEL_INFO, String.format("Cache [%s]: Size=%d, Hit Rate=%.1f%% (%d/%d)",
                cacheType, size, hitRate, hits, total));
    }

    /**
     * Checks if debug mode is currently enabled
     *
     * @return true if debug mode is enabled
     */
    public boolean isDebugEnabled() {
        return debugEnabled.get();
    }

    /**
     * Manually enables or disables debug mode
     *
     * @param enabled Whether debug should be enabled
     */
    public void setDebugEnabled(boolean enabled) {
        debugEnabled.set(enabled);
        log(LEVEL_INFO, "Debug mode " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * Processes queued log messages
     */
    private void processLogQueue() {
        // Process up to 10 messages per call to prevent spam
        int processed = 0;
        LogEntry entry;

        while (processed < 10 && (entry = logQueue.poll()) != null) {
            plugin.getLogger().info("[DEBUG] " + entry.message);
            processed++;
        }

        // If queue is getting too large, clear old entries
        if (logQueue.size() > 100) {
            int cleared = 0;
            while (logQueue.size() > 50 && logQueue.poll() != null) {
                cleared++;
            }
            if (cleared > 0) {
                plugin.getLogger().warning("Cleared " + cleared + " old debug messages due to queue overflow");
            }
        }
    }

    /**
     * Gets string representation of log level
     *
     * @param level Log level
     * @return Level string
     */
    private String getLevelString(int level) {
        return switch (level) {
            case LEVEL_TRACE -> "TRACE";
            case LEVEL_DEBUG -> "DEBUG";
            case LEVEL_INFO -> "INFO";
            case LEVEL_WARN -> "WARN";
            case LEVEL_ERROR -> "ERROR";
            default -> "UNKNOWN";
        };
    }

    /**
     * Flushes all pending log messages
     */
    public void flush() {
        if (!debugEnabled.get()) {
            return;
        }

        LogEntry entry;
        while ((entry = logQueue.poll()) != null) {
            plugin.getLogger().info("[DEBUG] " + entry.message);
        }
    }

    /**
     * Shuts down the debugger and flushes remaining messages
     */
    public void shutdown() {
        log(LEVEL_INFO, "Debugger shutting down...");
        flush();
    }

    /**
     * Creates a scoped debug context for measuring operation time
     *
     * @param operationName Name of the operation
     * @return DebugContext for automatic timing
     */
    public DebugContext createContext(String operationName) {
        return new DebugContext(this, operationName);
    }

    /**
     * Record class for log entries
     */
    private record LogEntry(long timestamp, int level, String message) {}

    /**
     * Debug context for automatic operation timing
     */
    public static class DebugContext implements AutoCloseable {
        private final Debugger debugger;
        private final String operationName;
        private final long startTime;

        public DebugContext(Debugger debugger, String operationName) {
            this.debugger = debugger;
            this.operationName = operationName;
            this.startTime = System.currentTimeMillis();

            debugger.trace("Started: " + operationName);
        }

        @Override
        public void close() {
            debugger.logPerformance(operationName, startTime);
        }
    }
}