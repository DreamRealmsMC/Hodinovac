# Hodinovac

Advanced playtime tracking plugin for Minecraft Paper servers with AFK detection and comprehensive API.

## Features

- 🕐 **Precise Playtime Tracking** - Tracks player playtime excluding AFK time
- 💤 **Smart AFK Detection** - Movement-based AFK detection with configurable timeout
- 🗄️ **MySQL Database** - Reliable data storage with HikariCP connection pooling
- 🔗 **PlaceholderAPI Integration** - Rich set of placeholders for other plugins
- 🚀 **Developer API** - Comprehensive API for plugin integrations
- ⚡ **High Performance** - Async operations, thread-safe caching, minimal server impact
- 🎮 **Admin Commands** - Easy management of player playtime data
- 🔄 **Real-time Updates** - Live session tracking and instant AFK status changes

## Requirements

- **Server Software**: Paper 1.19+ (or compatible)
- **Java Version**: Java 17+
- **Database**: MySQL 5.7+ or MariaDB 10.2+
- **Optional**: PlaceholderAPI (for placeholder functionality)

## Installation

1. Download the latest release from [GitHub Releases](../../releases)
2. Place the JAR file in your server's `plugins` folder
3. Start the server to generate the default configuration
4. Configure your MySQL database settings in `config.yml`
5. Restart the server

## Configuration

### Basic Setup

```yaml
# MySQL Database Configuration
mysql:
  host: "localhost"
  port: 3306
  database: "hodinovac"
  user: "root"
  password: "your_password"

# AFK Detection
afk:
  timeout_seconds: 300  # 5 minutes

# Playtime Display Formats
playtime_format_short: "%playtime_total_hours%h %playtime_total_minutes%m"
playtime_format_long: "%playtime_total_days%d %playtime_total_hours%h %playtime_total_minutes%m %playtime_total_seconds%s"
```

### AFK Configuration

```yaml
afk:
  timeout_seconds: 300  # Time before player is marked AFK
  format:
    afk: "AFK"          # Text shown when player is AFK
    not_afk: ""         # Text shown when player is active
```

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/playtime` | `hodinovac.check.self` | Check your own playtime |
| `/playtime check <player>` | `hodinovac.check.others` | Check another player's playtime |
| `/playtime add <player> <time>` | `hodinovac.add` | Add playtime to a player |
| `/playtime remove <player> <time>` | `hodinovac.remove` | Remove playtime from a player |
| `/playtime reload` | `hodinovac.reload` | Reload plugin configuration |

### Time Format Examples
- `1d` - 1 day
- `2h` - 2 hours
- `30m` - 30 minutes
- `45s` - 45 seconds
- `1h30m` - 1 hour 30 minutes
- `2d5h30m` - 2 days 5 hours 30 minutes

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `hodinovac.*` | op | Full access to all features |
| `hodinovac.use` | true | Basic permission to use the plugin |
| `hodinovac.check.self` | true | Check own playtime |
| `hodinovac.check.others` | op | Check other players' playtime |
| `hodinovac.add` | op | Add playtime to players |
| `hodinovac.remove` | op | Remove playtime from players |
| `hodinovac.reload` | op | Reload plugin configuration |
| `hodinovac.admin` | op | Full administrative access |

## PlaceholderAPI Placeholders

### Total Playtime
| Placeholder | Description |
|-------------|-------------|
| `%hodinovac_total_days%` | Total full days played (excluding AFK) |
| `%hodinovac_total_hours%` | Hours remainder after days |
| `%hodinovac_total_minutes%` | Minutes remainder after hours |
| `%hodinovac_total_seconds%` | Seconds remainder after minutes |
| `%hodinovac_total_seconds_raw%` | Total playtime in seconds (raw number) |
| `%hodinovac_total%` | Formatted total playtime (short format) |
| `%hodinovac_total_long%` | Formatted total playtime (long format) |

### Session Playtime
| Placeholder | Description |
|-------------|-------------|
| `%hodinovac_session_days%` | Current session days |
| `%hodinovac_session_hours%` | Current session hours remainder |
| `%hodinovac_session_minutes%` | Current session minutes remainder |
| `%hodinovac_session_seconds%` | Current session seconds remainder |
| `%hodinovac_session_seconds_raw%` | Session playtime in seconds (raw number) |
| `%hodinovac_session%` | Formatted session playtime (short format) |
| `%hodinovac_session_long%` | Formatted session playtime (long format) |

### AFK Status
| Placeholder | Description |
|-------------|-------------|
| `%hodinovac_afk_status%` | AFK status string from config |
| `%hodinovac_afk_boolean%` | Boolean true/false for AFK state |
| `%hodinovac_time_since_move%` | Time since last move in seconds |

### Utility
| Placeholder | Description |
|-------------|-------------|
| `%hodinovac_is_tracked%` | Whether player is currently tracked |
| `%hodinovac_afk_timeout%` | AFK timeout setting in seconds |

## Developer API

### Getting Started

Add Hodinovac as a dependency to your plugin and access the API:

```java
// Get the API instance
HodinovacAPI api = Hodinovac.getAPI();

// Check if API is available
if (api != null && api.isAvailable()) {
    // Use the API
}
```

### Basic Usage Examples

```java
import me.itzstanleex.hodinovac.api.HodinovacAPI;
import java.util.UUID;

// Get player's total playtime
UUID playerUUID = player.getUniqueId();
long playtimeSeconds = api.getPlaytime(playerUUID);

// Get formatted playtime
String shortFormat = api.getFormattedPlaytime(playerUUID, false);
String longFormat = api.getFormattedPlaytime(playerUUID, true);

// Check if player is AFK
boolean isAfk = api.isAfk(playerUUID);

// Get session playtime
long sessionSeconds = api.getSessionPlaytime(playerUUID);
```

### Async Operations

```java
// Get playtime asynchronously (recommended for offline players)
api.getPlaytimeAsync(playerUUID).thenAccept(playtime -> {
    // Handle the result
    System.out.println("Player playtime: " + playtime + " seconds");
});

// Check if player has data in database
api.hasPlaytimeData(playerUUID).thenAccept(hasData -> {
    if (hasData) {
        // Player exists in database
    }
});
```

### Modifying Playtime

```java
// Add playtime (for rewards, etc.)
boolean success = api.addPlaytime(playerUUID, 3600); // Add 1 hour

// Remove playtime (for punishments, etc.)
boolean success = api.removePlaytime(playerUUID, 1800); // Remove 30 minutes

// Set AFK status manually
boolean success = api.setAfkStatus(playerUUID, true); // Mark as AFK
```

### API Reference

#### Core Methods

| Method | Description | Return Type |
|--------|-------------|-------------|
| `getPlaytime(UUID)` | Get total playtime in seconds | `long` |
| `getPlaytimeAsync(UUID)` | Get total playtime asynchronously | `CompletableFuture<Long>` |
| `getFormattedPlaytime(UUID, boolean)` | Get formatted playtime string | `String` |
| `getSessionPlaytime(UUID)` | Get current session playtime | `long` |
| `isAfk(UUID)` | Check if player is AFK | `boolean` |
| `addPlaytime(UUID, long)` | Add playtime to player | `boolean` |
| `removePlaytime(UUID, long)` | Remove playtime from player | `boolean` |
| `setAfkStatus(UUID, boolean)` | Set player's AFK status | `boolean` |

#### Utility Methods

| Method | Description | Return Type |
|--------|-------------|-------------|
| `isPlayerTracked(UUID)` | Check if player is being tracked | `boolean` |
| `getTimeSinceLastMove(UUID)` | Time since player last moved | `long` |
| `getAfkTimeoutSeconds()` | Get AFK timeout setting | `long` |
| `hasPlaytimeData(UUID)` | Check if player exists in database | `CompletableFuture<Boolean>` |
| `savePlayerData(UUID)` | Force save player data | `CompletableFuture<Boolean>` |
| `isAvailable()` | Check if API is ready | `boolean` |

### Events

Listen for AFK status changes:

```java
@EventHandler
public void onAfkStatusChange(PlayerAfkStatusChangeEvent event) {
    UUID playerUUID = event.getPlayerUUID();
    boolean isAfk = event.isAfk();
    
    if (event.wentAfk()) {
        // Player went AFK
        Player player = event.getPlayer();
        if (player != null) {
            player.sendMessage("You are now AFK");
        }
    } else if (event.returnedFromAfk()) {
        // Player returned from AFK
        Player player = event.getPlayer();
        if (player != null) {
            player.sendMessage("Welcome back!");
        }
    }
}
```

### Maven Dependency

```xml
<dependency>
    <groupId>me.itzstanleex</groupId>
    <artifactId>hodinovac</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

## Changelog

### v1.0.0
- Initial release
- Basic playtime tracking with AFK detection
- MySQL database integration
- PlaceholderAPI support
- Comprehensive developer API
- Admin commands for playtime management

---

**Made with ❤️ by [ItzStanleex](https://github.com/itzstanleex)**