package dev.lorenzz.frankie.config;

import dev.lorenzz.frankie.util.Text;
import net.kyori.adventure.text.Component;

import java.util.List;

/**
 * Access to messages.yml. %prefix% resolves to the prefix of the message's
 * own section (staffchat.*, report.*, friend.* ...).
 */
public class Messages {

    private final YamlConfig config;

    public Messages(YamlConfig config) {
        this.config = config;
    }

    /**
     * Raw string with %prefix% and the given placeholder pairs applied.
     * Placeholders are passed as (key, value, key, value, ...).
     */
    public String raw(String path, String... placeholders) {
        String message = config.getString(path, "<missing message: " + path + ">");
        return apply(sectionOf(path), message, placeholders);
    }

    public Component get(String path, String... placeholders) {
        return Text.colorize(raw(path, placeholders));
    }

    public List<Component> getList(String path, String... placeholders) {
        String section = sectionOf(path);
        return config.getStringList(path).stream()
                .map(line -> Text.colorize(apply(section, line, placeholders)))
                .toList();
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