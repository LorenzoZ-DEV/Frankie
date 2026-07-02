package dev.lorenzz.frankie.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Colorizes strings using legacy codes (&a, &l, ...) and hex codes (&#RRGGBB).
 */
public final class Text {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private Text() {
    }

    public static Component colorize(String input) {
        return SERIALIZER.deserialize(input);
    }
}