package dev.lorenzz.frankie.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class Text {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private Text() {
    }

    public static Component colorize(String input) {
        if (input == null) return Component.empty();
        return SERIALIZER.deserialize(input);
    }
}