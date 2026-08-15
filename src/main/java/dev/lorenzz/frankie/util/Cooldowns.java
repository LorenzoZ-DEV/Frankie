package dev.lorenzz.frankie.util;

import java.util.Map;
import java.util.UUID;

/** Per-player command cooldowns. Returns seconds remaining, rounded up. */
public final class Cooldowns {

    private Cooldowns() {
    }

    /** @return seconds left before {@code id} may act again, 0 if ready. */
    public static long remaining(Map<UUID, Long> stamps, UUID id, long cooldownSeconds) {
        Long last = stamps.get(id);
        if (last == null) {
            return 0;
        }
        long cooldownMillis = cooldownSeconds * 1000L;
        long elapsed = System.currentTimeMillis() - last;
        return elapsed >= cooldownMillis ? 0 : (cooldownMillis - elapsed + 999) / 1000;
    }
}
