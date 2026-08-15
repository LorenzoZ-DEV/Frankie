package dev.lorenzz.frankie.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Collection;
import java.util.Iterator;

/**
 * The single legacy-colour serializer for the whole plugin: {@code &} codes plus
 * {@code &#RRGGBB} hex.
 *
 * <p>Everything user-facing must go through here. Sending a raw
 * {@code Component.text(...)} of a config string shows the colour codes to the
 * player instead of colouring the line, and building a second serializer without
 * {@code hexColors()} silently drops every hex colour.
 */
public final class Text {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private Text() {
    }

    public static Component colorize(String input) {
        if (input == null) {
            return Component.empty();
        }
        return SERIALIZER.deserialize(input);
    }

    /**
     * Colourises each line and joins them with newlines (kick/MOTD screens).
     *
     * <p>Built through a single builder: appending to an immutable component in a
     * loop copied the whole child list on every line.
     */
    public static Component colorizeLines(Collection<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return Component.empty();
        }
        TextComponent.Builder builder = Component.text();
        for (Iterator<String> it = lines.iterator(); it.hasNext(); ) {
            builder.append(colorize(it.next()));
            if (it.hasNext()) {
                builder.append(Component.newline());
            }
        }
        return builder.build();
    }
}
