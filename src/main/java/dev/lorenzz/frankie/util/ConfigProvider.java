package dev.lorenzz.frankie.util;

import dev.lorenzz.frankie.config.YamlConfig;

public class ConfigProvider {
    private static YamlConfig config;

    public static void setConfig(YamlConfig config) {
        ConfigProvider.config = config;
    }

    public static boolean getBoolean(String path, boolean def) {
        if (config == null) return def;
        return config.getBoolean(path, def);
    }

    public static String getString(String path, String def) {
        if (config == null) return def;
        return config.getString(path, def);
    }
}