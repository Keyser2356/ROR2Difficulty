package com.ror2difficulty;

import com.ror2difficulty.manager.DifficultyManager;
import com.ror2difficulty.manager.DisplayManager;
import com.ror2difficulty.manager.EventsManager;
import com.ror2difficulty.manager.MobBehaviorManager;
import com.ror2difficulty.manager.LocalizationManager;
import com.ror2difficulty.manager.DragonBossManager;
import com.ror2difficulty.listener.DifficultyEventListener;
import com.ror2difficulty.listener.GameplayListener;
import com.ror2difficulty.listener.DragonBattleListener;
import com.ror2difficulty.command.DifficultyCommand;
import com.ror2difficulty.command.DifficultyAdminCommand;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class ROR2DifficultyPlugin extends JavaPlugin {

    private DifficultyManager difficultyManager;
    private DisplayManager displayManager;
    private EventsManager eventsManager;
    private MobBehaviorManager mobBehaviorManager;
    private LocalizationManager localizationManager;
    private DragonBossManager dragonBossManager;

    @Override
    public void onEnable() {
        getLogger().info("═══════════════════════════════════════");
        getLogger().info("ROR2Difficulty плагин запускается...");
        getLogger().info("═══════════════════════════════════════");

        // Загрузка конфига с учетом языка
        selectConfigByLanguage();
        reloadConfig();

        // Менеджер локализации
        localizationManager = new LocalizationManager(this);

        // Инициализация менеджера сложности
        difficultyManager = new DifficultyManager(this, localizationManager);
        difficultyManager.initialize();

        // Менеджер отображения
        displayManager = new DisplayManager(this, difficultyManager, localizationManager);
        difficultyManager.setOnChange(mult -> displayManager.refreshNow());

        // Менеджер событий
        eventsManager = new EventsManager(this, difficultyManager);

        // Менеджер поведения мобов
        mobBehaviorManager = new MobBehaviorManager(this, difficultyManager);

        // Менеджер босс-битвы с драконом
        dragonBossManager = new DragonBossManager(this, difficultyManager);

        // Регистрация слушателей событий
        getServer().getPluginManager().registerEvents(
                new DifficultyEventListener(difficultyManager, this),
                this
        );
        getServer().getPluginManager().registerEvents(
            new GameplayListener(this, difficultyManager),
            this
        );
        getServer().getPluginManager().registerEvents(
            new DragonBattleListener(dragonBossManager),
            this
        );

        // Регистрация команд
        getCommand("difficulty").setExecutor(new DifficultyCommand(difficultyManager));
        getCommand("difficultyadmin").setExecutor(new DifficultyAdminCommand(difficultyManager, this));

        // Запуск асинхронного таска для увеличения сложности по времени
        startDifficultyTicker();
        displayManager.start();
        displayManager.refreshNow();
        eventsManager.start();
        mobBehaviorManager.start();
        dragonBossManager.start();

        getLogger().info("ROR2Difficulty успешно загружен!");
        getLogger().info("═══════════════════════════════════════");
    }

    @Override
    public void onDisable() {
        getLogger().info("ROR2Difficulty плагин отключается...");
        if (difficultyManager != null) {
            difficultyManager.saveDifficultyData();
        }
        if (displayManager != null) {
            displayManager.stop();
        }
        if (eventsManager != null) {
            eventsManager.stop();
        }
        if (mobBehaviorManager != null) {
            mobBehaviorManager.stop();
        }
        if (dragonBossManager != null) {
            dragonBossManager.stop();
        }
    }

    /**
     * Запускает таск для увеличения сложности по времени
     */
    private void startDifficultyTicker() {
        if (!getConfig().getBoolean("time-scaling.enabled", true)) {
            return;
        }

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                return;
            }

            double increasePerMinute = getConfig().getDouble("time-scaling.multiplier-per-minute", 0.01);
            difficultyManager.addDifficulty(increasePerMinute);
        }, 20L * 60, 20L * 60); // Каждую минуту (20 тиков * 60 = 1200 тиков)
    }

    /**
     * Выбирает конфиг в зависимости от языка
     * EN - по умолчанию, RU - если указано
     */
    private void selectConfigByLanguage() {
        java.io.File dataFolder = getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        java.io.File configFile = new java.io.File(dataFolder, "config.yml");
        
        if (configFile.exists()) {
            ensureLangKey(configFile);
            cleanupLegacyConfigs();
            return;
        }

        String selectedLanguage = detectDefaultLanguage();
        copyLanguageConfig(selectedLanguage);
    }

    /**
     * Копирует языковой конфиг в основной config.yml
     */
    private void copyLanguageConfig(String language) {
        java.io.File dataFolder = getDataFolder();
        String sourceFileName = language.equalsIgnoreCase("RU") ? "config_ru.yml" : "config_en.yml";
        java.io.File targetFile = new java.io.File(dataFolder, "config.yml");

        try (java.io.InputStream in = getResource(sourceFileName)) {
            if (in == null) {
                saveDefaultConfig();
                return;
            }

            java.nio.file.Files.copy(
                in,
                targetFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );

            org.bukkit.configuration.file.YamlConfiguration cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(targetFile);
            cfg.set("lang", language.toUpperCase());
            cfg.save(targetFile);

            cleanupLegacyConfigs();
            getLogger().info("✓ Конфиг создан: " + language);
        } catch (Exception e) {
            getLogger().severe("Ошибка при копировании конфига: " + e.getMessage());
        }
    }

    private void ensureLangKey(java.io.File configFile) {
        try {
            org.bukkit.configuration.file.YamlConfiguration cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile);
            if (!cfg.isString("lang")) {
                String detected = detectDefaultLanguage();
                cfg.set("lang", detected);
                cfg.save(configFile);
                getLogger().info("Добавлен отсутствующий ключ lang в config.yml: " + detected);
            }
        } catch (Exception e) {
            getLogger().warning("Не удалось проверить lang в config.yml: " + e.getMessage());
        }
    }

    private String detectDefaultLanguage() {
        String systemLanguage = System.getProperty("user.language", "en");
        return systemLanguage.startsWith("ru") ? "RU" : "EN";
    }

    private void cleanupLegacyConfigs() {
        java.io.File dataFolder = getDataFolder();
        java.io.File ru = new java.io.File(dataFolder, "config_ru.yml");
        java.io.File en = new java.io.File(dataFolder, "config_en.yml");
        if (ru.exists()) {
            ru.delete();
        }
        if (en.exists()) {
            en.delete();
        }
    }

    public DifficultyManager getDifficultyManager() {
        return difficultyManager;
    }
}
