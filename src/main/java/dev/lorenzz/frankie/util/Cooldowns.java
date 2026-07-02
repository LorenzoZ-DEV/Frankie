package dev.lorenzz.frankie.util;

import java.util.HashMap;
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

    // ponytail: self-check for the round-up boundary. run: java -ea Cooldowns
    public static void main(String[] args) {
        Map<UUID, Long> stamps = new HashMap<>();
        UUID id = UUID.randomUUID();
        assert remaining(stamps, id, 60) == 0 : "no stamp = ready";
        stamps.put(id, System.currentTimeMillis());
        long r = remaining(stamps, id, 60);
        assert r >= 59 && r <= 60 : "just used = ~60s left, got " + r;
        stamps.put(id, System.currentTimeMillis() - 60_000);
        assert remaining(stamps, id, 60) == 0 : "elapsed = ready";
        System.out.println("Cooldowns OK");
    }
}