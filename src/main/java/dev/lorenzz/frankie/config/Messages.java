package dev.lorenzz.frankie.config;

import dev.lorenzz.frankie.util.Text;
import net.kyori.adventure.text.Component;

import java.util.List;

/**
 * Reads messages.yml and turns entries into coloured components.
 *
 * <p>Placeholders are passed as flat key/value pairs:
 * {@code messages.get("friend.removed", "%target%", name)}.
 */
public class Messages {

    private final YamlConfig config;

    public Messages(YamlConfig config) {
        this.config = config;
    }

    /** Raw (uncoloured) text with placeholders applied. */
    public String raw(String path, String... placeholders) {
        String message = config.getString(path, "<missing message: " + path + ">");
        return apply(sectionOf(path), message, placeholders);
    }

    public Component get(String path, String... placeholders) {
        return Text.colorize(raw(path, placeholders));
    }

    public List<Component> getList(String path, String... placeholders) {
        return getStringList(path, placeholders).stream()
                .map(Text::colorize)
                .toList();
    }

    public List<String> getStringList(String path, String... placeholders) {
        String section = sectionOf(path);
        return config.getStringList(path).stream()
                .map(line -> apply(section, line, placeholders))
                .toList();
    }

    /** A string list rendered as one component with newlines, for kick screens. */
    public Component getMultiline(String path, String... placeholders) {
        return Text.colorizeLines(getStringList(path, placeholders));
    }

    private String sectionOf(String path) {
        int dot = path.indexOf('.');
        return dot == -1 ? "" : path.substring(0, dot);
    }

    private String apply(String section, String message, String... placeholders) {
        String prefix = config.getString(section + ".prefix", "");
        String result = message.replace("%prefix%", prefix);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            result = result.replace(placeholders[i], placeholders[i + 1]);
        }
        return result;
    }
}
