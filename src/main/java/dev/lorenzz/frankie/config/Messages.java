package dev.lorenzz.frankie.config;

import dev.lorenzz.frankie.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

import java.util.Collection;
import java.util.List;

public class Messages {

    private final YamlConfig config;

    public Messages(YamlConfig config) {
        this.config = config;
    }

    public String raw(String path, String... placeholders) {
        String message = config.getString(path, "<missing message: " + path + ">");
        return apply(sectionOf(path), message, placeholders);
    }

    public Component get(String path, String... placeholders) {
        return Text.colorize(raw(path, placeholders));
    }

    public Collection<TextComponent> getList(String path, String... placeholders) {
        String section = sectionOf(path);
        return config.getStringList(path).stream()
                .map(line -> (TextComponent) Text.colorize(apply(section, line, placeholders)))
                .toList();
    }

    public List<String> getStringList(String path, String... placeholders) {
        String section = sectionOf(path);
        return config.getStringList(path).stream()
                .map(line -> apply(section, line, placeholders))
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