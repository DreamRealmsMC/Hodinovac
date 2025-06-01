package me.itzstanleex.hodinovac.api.events;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Event fired when a player's AFK status changes.
 *
 * This event is called whenever a player transitions between AFK and non-AFK states.
 * It provides information about the player and their new AFK status, allowing other
 * plugins to react to AFK status changes.
 *
 * Example usage:
 * <pre>
 * {@code
 * @EventHandler
 * public void onAfkStatusChange(PlayerAfkStatusChangeEvent event) {
 *     Player player = event.getPlayer();
 *     if (event.isAfk()) {
 *         // Player went AFK
 *         player.sendMessage("You are now AFK");
 *     } else {
 *         // Player is no longer AFK
 *         player.sendMessage("Welcome back!");
 *     }
 * }
 * }
 * </pre>
 *
 * @author ItzStanleyX
 * @version 1.0.0
 * @since 1.0.0
 */
public class PlayerAfkStatusChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    @Getter
    private final UUID playerUUID;

    @Getter
    private final boolean afk;

    @Getter
    private final long timestamp;

    /**
     * Constructor for PlayerAfkStatusChangeEvent
     *
     * @param playerUUID UUID of the player whose AFK status changed
     * @param afk New AFK status (true = AFK, false = not AFK)
     */
    public PlayerAfkStatusChangeEvent(UUID playerUUID, boolean afk) {
        super(false); // This event is not async

        if (playerUUID == null) {
            throw new IllegalArgumentException("Player UUID cannot be null");
        }

        this.playerUUID = playerUUID;
        this.afk = afk;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Constructor for PlayerAfkStatusChangeEvent with custom timestamp
     *
     * @param playerUUID UUID of the player whose AFK status changed
     * @param afk New AFK status (true = AFK, false = not AFK)
     * @param timestamp Custom timestamp for the event
     */
    public PlayerAfkStatusChangeEvent(UUID playerUUID, boolean afk, long timestamp) {
        super(false); // This event is not async

        if (playerUUID == null) {
            throw new IllegalArgumentException("Player UUID cannot be null");
        }

        this.playerUUID = playerUUID;
        this.afk = afk;
        this.timestamp = timestamp;
    }

    /**
     * Gets the Player object associated with this event.
     *
     * @return Player object, or null if player is offline
     * @since 1.0.0
     */
    public Player getPlayer() {
        return Bukkit.getPlayer(playerUUID);
    }

    /**
     * Gets the player's name.
     *
     * This method attempts to get the name from the online player first,
     * then falls back to the offline player data if available.
     *
     * @return Player's name, or "Unknown" if cannot be determined
     * @since 1.0.0
     */
    public String getPlayerName() {
        Player player = getPlayer();
        if (player != null) {
            return player.getName();
        }

        // Try to get from offline player data
        try {
            org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);
            String name = offlinePlayer.getName();
            return name != null ? name : "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }

    /**
     * Checks if the player went AFK (transitioned from active to AFK).
     *
     * @return true if player went AFK, false otherwise
     * @since 1.0.0
     */
    public boolean wentAfk() {
        return afk;
    }

    /**
     * Checks if the player returned from AFK (transitioned from AFK to active).
     *
     * @return true if player returned from AFK, false otherwise
     * @since 1.0.0
     */
    public boolean returnedFromAfk() {
        return !afk;
    }

    /**
     * Checks if the player is online.
     *
     * @return true if player is online, false otherwise
     * @since 1.0.0
     */
    public boolean isPlayerOnline() {
        Player player = getPlayer();
        return player != null && player.isOnline();
    }

    /**
     * Gets the time elapsed since the event occurred.
     *
     * @return Time elapsed in milliseconds since the event
     * @since 1.0.0
     */
    public long getTimeElapsed() {
        return System.currentTimeMillis() - timestamp;
    }

    /**
     * Gets a human-readable description of the AFK status change.
     *
     * @return Description string like "Player went AFK" or "Player returned from AFK"
     * @since 1.0.0
     */
    public String getDescription() {
        String playerName = getPlayerName();
        return afk ? playerName + " went AFK" : playerName + " returned from AFK";
    }

    /**
     * Creates a formatted message suitable for display.
     *
     * @param afkMessage Message to show when player goes AFK
     * @param returnMessage Message to show when player returns from AFK
     * @return Formatted message with player name replaced
     * @since 1.0.0
     */
    public String getFormattedMessage(String afkMessage, String returnMessage) {
        String message = afk ? afkMessage : returnMessage;
        String playerName = getPlayerName();

        return message.replace("{player}", playerName)
                .replace("{Player}", playerName)
                .replace("%player%", playerName)
                .replace("%Player%", playerName);
    }

    /**
     * Required method for Bukkit events - returns the handler list.
     *
     * @return HandlerList for this event
     */
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Required static method for Bukkit events - returns the handler list.
     *
     * @return HandlerList for this event
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * Returns a string representation of this event.
     *
     * @return String representation including player UUID, AFK status, and timestamp
     */
    @Override
    public String toString() {
        return "PlayerAfkStatusChangeEvent{" +
                "playerUUID=" + playerUUID +
                ", afk=" + afk +
                ", timestamp=" + timestamp +
                ", playerName='" + getPlayerName() + '\'' +
                '}';
    }

    /**
     * Checks if this event equals another object.
     *
     * @param obj Object to compare with
     * @return true if objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        PlayerAfkStatusChangeEvent that = (PlayerAfkStatusChangeEvent) obj;

        return afk == that.afk &&
                timestamp == that.timestamp &&
                playerUUID.equals(that.playerUUID);
    }

    /**
     * Returns hash code for this event.
     *
     * @return Hash code based on playerUUID, AFK status, and timestamp
     */
    @Override
    public int hashCode() {
        int result = playerUUID.hashCode();
        result = 31 * result + (afk ? 1 : 0);
        result = 31 * result + (int) (timestamp ^ (timestamp >>> 32));
        return result;
    }
}