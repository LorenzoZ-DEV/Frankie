package dev.lorenzz.frankie.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple YAML wrapper with dotted-path lookups. The default file is copied
 * from the plugin jar on first start. Supports live reload via {@link #reload()}.
 *
 * <p>The tree is flattened into dotted keys once, at load time. Lookups used to
 * {@code path.split("\\.")} — compiling a regex and walking a map per level — on
 * every single read, and every chat message costs at least two reads (the text
 * and its section prefix). Now a read is one hash lookup.
 */
public class YamlConfig {

    private final Path file;
    private volatile Map<String, Object> values;

    private YamlConfig(Path file, Map<String, Object> root) {
        this.file = file;
        this.values = flatten(root);
    }

    public static YamlConfig load(Path dataDirectory, String fileName) throws IOException {
        Files.createDirectories(dataDirectory);
        Path file = dataDirectory.resolve(fileName);
        if (Files.notExists(file)) {
            try (InputStream in = YamlConfig.class.getClassLoader().getResourceAsStream(fileName)) {
                if (in == null) {
                    throw new IOException("Default resource " + fileName + " not found in jar");
                }
                Files.copy(in, file);
            }
        }
        return new YamlConfig(file, read(file));
    }

    /** Re-reads the file from disk. On failure the old values stay active. */
    public void reload() throws IOException {
        // Flatten before publishing: a half-built map must never be visible.
        Map<String, Object> reloaded = flatten(read(file));
        this.values = reloaded;
    }

    private static Map<String, Object> read(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return new Yaml().load(in);
        }
    }

    /** Turns the nested tree into {@code section.key} entries, once per load. */
    private static Map<String, Object> flatten(Map<String, Object> root) {
        if (root == null || root.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> flat = new HashMap<>();
        flatten("", root, flat);
        return Collections.unmodifiableMap(flat);
    }

    private static void flatten(String prefix, Map<?, ?> map, Map<String, Object> out) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = prefix.isEmpty()
                    ? String.valueOf(entry.getKey())
                    : prefix + '.' + entry.getKey();
            Object value = entry.getValue();
            out.put(key, value);
            // Sections stay reachable both as a whole and by their leaves.
            if (value instanceof Map<?, ?> child) {
                flatten(key, child, out);
            }
        }
    }

    private Object get(String path) {
        return values.get(path);
    }

    public String getString(String path, String def) {
        Object value = get(path);
        return value == null ? def : String.valueOf(value);
    }

    public int getInt(String path, int def) {
        Object value = get(path);
        return value instanceof Number number ? number.intValue() : def;
    }

    public long getLong(String path, long def) {
        Object value = get(path);
        return value instanceof Number number ? number.longValue() : def;
    }

    public double getDouble(String path, double def) {
        Object value = get(path);
        return value instanceof Number number ? number.doubleValue() : def;
    }

    @SuppressWarnings("unchecked")
    public List<String> getStringList(String path) {
        Object value = get(path);
        return value instanceof List<?> list ? (List<String>) list : Collections.emptyList();
    }

    /**
     * A whole section as key/value pairs, for settings whose keys are data
     * rather than a fixed schema (the antiswear thresholds, say).
     */
    public Map<String, Object> getMap(String path) {
        if (!(get(path) instanceof Map<?, ?> map)) {
            return Collections.emptyMap();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            copy.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copy;
    }

    public boolean getBoolean(String path, boolean def) {
        Object value = get(path);
        if (value == null) return def;
        if (value instanceof Boolean bool) return bool;
        if (value instanceof String str) {
            return Boolean.parseBoolean(str);
        }
        if (value instanceof Number num) {
            return num.intValue() != 0;
        }
        return def;
    }
}