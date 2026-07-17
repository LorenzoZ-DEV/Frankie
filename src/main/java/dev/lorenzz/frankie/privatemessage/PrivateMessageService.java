package dev.lorenzz.frankie.privatemessage;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Simple service to track private message conversations.
 */
public class PrivateMessageService {
    private final Map<UUID, UUID> lastTarget = new HashMap<>();
    public void setLastTalker(UUID playerUuid, UUID targetUuid) {
        lastTarget.put(playerUuid, targetUuid);
    }
    public UUID getLastTalker(UUID playerUuid) {
        return lastTarget.get(playerUuid);
    }
    public void clear(UUID playerUuid) {
        lastTarget.remove(playerUuid);
    }
}