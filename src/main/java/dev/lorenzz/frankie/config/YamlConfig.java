package dev.lorenzz.frankie.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Simple YAML wrapper with dotted-path lookups. The default file is copied
 * from the plugin jar on first start. Supports live reload via {@link #reload()}.
 */
public class YamlConfig {

    private final Path file;
    private volatile Map<String, Object> root;

    private YamlConfig(Path file, Map<String, Object> root) {
        this.file = file;
        this.root = root == null ? Collections.emptyMap() : root;
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
        Map<String, Object> reloaded = read(file);
        this.root = reloaded == null ? Collections.emptyMap() : reloaded;
    }

    private static Map<String, Object> read(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return new Yaml().load(in);
        }
    }

    private Object get(String path) {
        Object current = root;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
        }
        return current;
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

    @SuppressWarnings("unchecked")
    public List<String> getStringList(String path) {
        Object value = get(path);
        return value instanceof List<?> list ? (List<String>) list : Collections.emptyList();
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