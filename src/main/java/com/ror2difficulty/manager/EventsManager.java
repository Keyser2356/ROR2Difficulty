package com.ror2difficulty.manager;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class EventsManager {

    private final Plugin plugin;
    private final DifficultyManager difficultyManager;
    private BukkitTask task;
    private double lastEventTrigger = -1;

    public EventsManager(Plugin plugin, DifficultyManager difficultyManager) {
        this.plugin = plugin;
        this.difficultyManager = difficultyManager;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("events.enabled", true)) {
            return;
        }

        long periodTicks = Math.max(1, plugin.getConfig().getLong("events.update-interval-seconds", 5)) * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::checkAndTriggerEvents, 20L, periodTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void checkAndTriggerEvents() {
        double current = difficultyManager.getCurrentMultiplier();
        
        // Получаем пороговые значения из конфига
        java.util.List<Double> thresholds = new java.util.ArrayList<>(
            plugin.getConfig().getConfigurationSection("events.by-difficulty")
                .getKeys(false)
                .stream()
                .map(Double::parseDouble)
                .sorted()
                .toList()
        );

        // Ищем наибольший порог, который был достигнут
        for (int i = thresholds.size() - 1; i >= 0; i--) {
            double threshold = thresholds.get(i);
            if (current >= threshold && threshold > lastEventTrigger) {
                triggerEvent(threshold, current);
                lastEventTrigger = threshold;
                break;
            }
        }
    }

    private void triggerEvent(double threshold, double current) {
        String path = "events.by-difficulty." + threshold;
        org.bukkit.configuration.ConfigurationSection config = plugin.getConfig().getConfigurationSection(path);
        
        if (config == null) {
            return;
        }

        String description = config.getString("description", "Событие сложности!");
        String particle = config.getString("particles", "ENCHANTED_HIT");
        int count = config.getInt("particle-count", 5);
        String effect = config.getString("effect", "");

        // Показываем сообщение всем игрокам
        Component msg = Component.text("⚡ " + description)
            .color(NamedTextColor.RED);
        Bukkit.broadcast(msg);

        // Спауним партиклы в центре мира
        spawnGlobalParticles(particle, count);

        // Применяем эффекты
        applyEffects(effect);
    }

    private void spawnGlobalParticles(String particleName, int count) {
        try {
            Particle particle = Particle.valueOf(particleName.toUpperCase());
            
            for (World world : Bukkit.getWorlds()) {
                // Спауним частицы в центре мира на высоте верхних блоков
                int x = 0, z = 0;
                int y = world.getHighestBlockYAt(x, z);
                
                world.spawnParticle(particle, x, y, z, count, 50, 50, 50, 1);
            }
        } catch (IllegalArgumentException ignored) {
            // Неверное имя партикла
        }
    }

    private void applyEffects(String effectString) {
        if (effectString == null || effectString.isEmpty()) {
            return;
        }

        String[] parts = effectString.split(",");
        
        for (String part : parts) {
            String[] kv = part.split(":");
            if (kv.length < 2) continue;
            
            String key = kv[0].trim();
            String value = kv[1].trim();

            if ("fog_duration".equals(key)) {
                int ticks = (int) (Integer.parseInt(value) * 20);
                applyFog(ticks);
            } else if ("effect".equals(key)) {
                // effect:WITHER,strength:1,duration:15
                // Уже обработано в других частях
            } else if ("storm".equals(key)) {
                if ("true".equalsIgnoreCase(value)) {
                    for (World world : Bukkit.getWorlds()) {
                        world.setStorm(true);
                        world.setThundering(true);
                        world.setWeatherDuration(12000); // 10 минут
                    }
                }
            }
        }
    }

    private void applyFog(int ticks) {
        // На Paper 1.21 можно использовать встроенные эффекты
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Слегка затемняем небо через погоду
            player.getWorld().setStorm(true);
        }
    }
}
