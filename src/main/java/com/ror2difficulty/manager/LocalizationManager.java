package com.ror2difficulty.manager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class LocalizationManager {

    private final Plugin plugin;
    private final String defaultLocaleTag;
    private final Map<String, Map<String, String>> localeMessages = new HashMap<>();

    public LocalizationManager(Plugin plugin) {
        this.plugin = plugin;
        this.defaultLocaleTag = normalizeLocaleTag(plugin.getConfig().getString("lang", "EN"));
        ensureLangFiles();
        loadMessages();
    }

    private void ensureLangFiles() {
        saveResourceIfMissing("lang/en_US.yml");
        saveResourceIfMissing("lang/ru_RU.yml");
    }

    private void saveResourceIfMissing(String path) {
        File out = new File(plugin.getDataFolder(), path);
        if (!out.exists()) {
            out.getParentFile().mkdirs();
            try (InputStream in = plugin.getResource(path)) {
                if (in != null) {
                    Files.copy(in, out.toPath());
                }
            } catch (Exception ignored) {}
        }
    }

    private void loadMessages() {
        loadLocaleFile("en_US");
        loadLocaleFile("ru_RU");
    }

    private void loadLocaleFile(String localeTag) {
        File file = new File(plugin.getDataFolder(), "lang/" + localeTag + ".yml");
        if (!file.exists()) {
            file = new File(plugin.getDataFolder(), "lang/en_US.yml");
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        Map<String, String> localized = new HashMap<>();
        for (String key : cfg.getKeys(true)) {
            if (cfg.isString(key)) {
                localized.put(key, cfg.getString(key, key));
            }
        }
        localeMessages.put(localeTag, localized);
    }

    public String resolveLocale(String clientLocale) {
        if (clientLocale == null) {
            return defaultLocaleTag;
        }
        String normalized = normalizeLocaleTag(clientLocale);
        if (localeMessages.containsKey(normalized)) {
            return normalized;
        }
        return "en_US";
    }

    private String normalizeLocaleTag(String localeValue) {
        if (localeValue == null) {
            return "en_US";
        }
        String lower = localeValue.toLowerCase(Locale.ROOT);
        if (lower.startsWith("ru")) {
            return "ru_RU";
        }
        return "en_US";
    }

    public String get(String key) {
        return get(key, defaultLocaleTag, key);
    }

    public String get(String key, String clientLocale, String fallback) {
        String localeTag = resolveLocale(clientLocale);
        Map<String, String> localized = localeMessages.getOrDefault(localeTag, localeMessages.get("en_US"));
        if (localized != null && localized.containsKey(key)) {
            return localized.get(key);
        }
        Map<String, String> en = localeMessages.get("en_US");
        if (en != null && en.containsKey(key)) {
            return en.get(key);
        }
        return fallback;
    }

    public String getStageLocalized(String stageKey) {
        return getStageLocalized(stageKey, defaultLocaleTag);
    }

    public String getStageLocalized(String stageKey, String clientLocale) {
        return get("stage." + stageKey, clientLocale, stageKey);
    }

    public String getUI(String key, String fallback) {
        return getUI(key, defaultLocaleTag, fallback);
    }

    public String getUI(String key, String clientLocale, String fallback) {
        return get("ui." + key, clientLocale, fallback);
    }
}
