package com.ror2difficulty.manager;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

import java.util.function.Consumer;

public class DifficultyManager {

    private final Plugin plugin;
    private double currentMultiplier;
    private double maxMultiplier;
    private double minMultiplier;
    private long sessionStartTime;
    private Consumer<Double> onChange;
    private boolean hardcore;
    private final LocalizationManager localizationManager;

    public DifficultyManager(Plugin plugin, LocalizationManager localizationManager) {
        this.plugin = plugin;
        this.localizationManager = localizationManager;
        this.sessionStartTime = System.currentTimeMillis();
    }

    /**
     * Инициализирует менеджер сложности из конфига
     */
    public void initialize() {
        this.currentMultiplier = plugin.getConfig().getDouble("difficulty.initial-multiplier", 1.0);
        this.maxMultiplier = plugin.getConfig().getDouble("difficulty.max-multiplier", 10.0);
        this.minMultiplier = plugin.getConfig().getDouble("difficulty.min-multiplier", 0.5);
        this.hardcore = plugin.getConfig().getBoolean("difficulty.hardcore", true);
        if (hardcore) {
            this.maxMultiplier = Math.max(this.maxMultiplier, 100.0);
        }
        
        plugin.getLogger().info("Менеджер сложности инициализирован:");
        plugin.getLogger().info("  Текущий множитель: " + currentMultiplier);
        plugin.getLogger().info("  Макс множитель: " + maxMultiplier);
        plugin.getLogger().info("  Мин множитель: " + minMultiplier);
    }

    /**
     * Добавляет сложность (положительное или отрицательное значение)
     */
    public void addDifficulty(double amount) {
        double oldMultiplier = currentMultiplier;
        double next = currentMultiplier + amount;

        if (!hardcore && currentMultiplier >= 10.0 && amount > 0) {
            next = currentMultiplier; // в не-хардкоре после 10.0 фиксируем рост
        }

        currentMultiplier = Math.max(minMultiplier, Math.min(maxMultiplier, next));
        
        if (Math.abs(oldMultiplier - currentMultiplier) > 0.001) {
            if (plugin.getConfig().getBoolean("logging.log-changes", true)) {
                plugin.getLogger().info(String.format(
                    "Сложность изменена: %.2f → %.2f (изменение: %+.3f)",
                    oldMultiplier, currentMultiplier, amount
                ));
            }
            notifyChange();
        }
    }

    /**
     * Устанавливает точное значение сложности
     */
    public void setDifficulty(double value) {
        double oldMultiplier = currentMultiplier;
        if (!hardcore && value > 10.0) {
            value = 10.0;
        }
        currentMultiplier = Math.max(minMultiplier, Math.min(maxMultiplier, value));
        
        if (plugin.getConfig().getBoolean("logging.log-changes", true)) {
            plugin.getLogger().info(String.format(
                "Сложность установлена: %.2f → %.2f",
                oldMultiplier, currentMultiplier
            ));
        }

        if (Math.abs(oldMultiplier - currentMultiplier) > 0.001) {
            notifyChange();
        }
    }

    /**
     * Получает текущий множитель сложности
     */
    public double getCurrentMultiplier() {
        return currentMultiplier;
    }

    /**
     * Получает процент максимальной сложности (0-100)
     */
    public double getDifficultyPercentage() {
        return ((currentMultiplier - minMultiplier) / (maxMultiplier - minMultiplier)) * 100;
    }

    public double getToHaha() {
        return Math.max(0, 10.0 - currentMultiplier);
    }

    public boolean isHardcore() {
        return hardcore;
    }

    public String getStageName() {
        return getStageName(null);
    }

    public String getStageName(String localeTag) {
        double m = currentMultiplier;
        String key;
        if (m < 1.5) key = "very_easy";
        else if (m < 3.0) key = "easy";
        else if (m < 4.5) key = "medium";
        else if (m < 6.0) key = "hard";
        else if (m < 8.0) key = "very_hard";
        else if (m < 9.0) key = "insane";
        else if (m < 10.0) key = "impossible";
        else if (m < 11.0) key = "i_see_you";
        else if (m < 12.0) key = "im_coming";
        else key = "haha";
        return localizationManager.getStageLocalized(key, localeTag);
    }

    /**
     * Получает ключ текущей стадии для конфига
     */
    public String getStageKey() {
        double m = currentMultiplier;
        if (m < 1.5) return "very_easy";
        else if (m < 3.0) return "easy";
        else if (m < 4.5) return "medium";
        else if (m < 6.0) return "hard";
        else if (m < 8.0) return "very_hard";
        else if (m < 9.0) return "insane";
        else if (m < 10.0) return "impossible";
        else return "haha";
    }

    /**
     * Применяет эффекты сложности к мобу
     * Использует систему стадий для более понятного баланса
     */
    public void applyDifficultyEffects(LivingEntity entity) {
        if (!(entity instanceof Mob mob)) {
            return;
        }

        // Определяем текущую стадию
        String stage = getStageKey();
        
        // Получаем коэффициенты для текущей стадии
        double healthAdd = plugin.getConfig().getDouble("effects.stage-thresholds." + stage + ".health-add", 0.1);
        double damageAdd = plugin.getConfig().getDouble("effects.stage-thresholds." + stage + ".damage-add", 0.05);
        double speedAdd = plugin.getConfig().getDouble("effects.stage-thresholds." + stage + ".speed-add", 0.02);
        double attackSpeedAdd = plugin.getConfig().getDouble("effects.mob-attack-speed-multiplier", 0.05);

        // Применяем формулу: base * (1 + добавка * (сложность - 1))
        double healthMultiplier = 1.0 + (healthAdd * (currentMultiplier - 1.0));
        double damageMultiplier = 1.0 + (damageAdd * (currentMultiplier - 1.0));
        double speedMultiplier = 1.0 + (speedAdd * (currentMultiplier - 1.0));
        double attackSpeedMultiplier = 1.0 + (attackSpeedAdd * (currentMultiplier - 1.0));

        // Применяем здоровье с ограничением на максимум 2048.0 (лимит Minecraft)
        double newHealth = Math.min(2048.0, entity.getMaxHealth() * healthMultiplier);
        entity.setMaxHealth(newHealth);
        entity.setHealth(Math.min(entity.getHealth() * healthMultiplier, newHealth));

        // Получаем атрибуты через registry
        Attribute attackDamage = RegistryAccess.registryAccess().getRegistry(RegistryKey.ATTRIBUTE).get(org.bukkit.NamespacedKey.minecraft("generic.attack_damage"));
        Attribute movementSpeed = RegistryAccess.registryAccess().getRegistry(RegistryKey.ATTRIBUTE).get(org.bukkit.NamespacedKey.minecraft("generic.movement_speed"));
        Attribute attackSpeed = RegistryAccess.registryAccess().getRegistry(RegistryKey.ATTRIBUTE).get(org.bukkit.NamespacedKey.minecraft("generic.attack_speed"));
        
        applyAttributeMultiplier(mob, attackDamage, damageMultiplier);
        applyAttributeMultiplier(mob, movementSpeed, speedMultiplier);
        if (attackSpeed != null) {
            applyAttributeMultiplier(mob, attackSpeed, attackSpeedMultiplier);
        }
    }

    /**
     * Обработка смерти игрока
     */
    public void onPlayerDeath() {
        if (!plugin.getConfig().getBoolean("death-penalty.enabled", true)) {
            return;
        }

        double decrease = plugin.getConfig().getDouble("death-penalty.multiplier-decrease", 0.15);
        double minimumAfterDeath = plugin.getConfig().getDouble("death-penalty.minimum-after-death", 0.5);
        
        double oldMultiplier = currentMultiplier;
        currentMultiplier = Math.max(minimumAfterDeath, currentMultiplier - decrease);
        
        if (plugin.getConfig().getBoolean("notifications.send-death-message", true)) {
            Bukkit.broadcast(
                net.kyori.adventure.text.Component.text("☠ Сложность снижена на смерть игрока! ")
                    .append(net.kyori.adventure.text.Component.text(String.format("(%.2f → %.2f)", oldMultiplier, currentMultiplier))
                    .color(net.kyori.adventure.text.format.NamedTextColor.RED))
            );
        }

        notifyChange();
    }

    /**
     * Обработка убийства моба
     */
    public void onMobKilled(String mobType) {
        if (!plugin.getConfig().getBoolean("kill-bonus.enabled", true)) {
            return;
        }

        double increase = 0.0;
        
        if (plugin.getConfig().getStringList("special-mobs.bosses_2").contains(mobType)) {
            increase = plugin.getConfig().getDouble("kill-bonus.multiplier-increase.boss_2", 0.01);
        } else if (plugin.getConfig().getStringList("special-mobs.bosses_1").contains(mobType)) {
            increase = plugin.getConfig().getDouble("kill-bonus.multiplier-increase.boss_1", 0.002);
        } else {
            increase = plugin.getConfig().getDouble("kill-bonus.multiplier-increase.mob", 0.0005);
        }

        if (increase > 0) {
            double oldMultiplier = currentMultiplier;
            currentMultiplier = Math.min(maxMultiplier, currentMultiplier + increase);
            
            if (plugin.getConfig().getBoolean("notifications.send-kill-message", true) && increase > 0.001) {
                Bukkit.broadcast(
                    net.kyori.adventure.text.Component.text("⚔ Сложность увеличена! ")
                        .append(net.kyori.adventure.text.Component.text(String.format("(%.2f → %.2f)", oldMultiplier, currentMultiplier))
                        .color(net.kyori.adventure.text.format.NamedTextColor.GOLD))
                );
            }

            notifyChange();
        }
    }

    /**
     * Сбрасывает сложность на начальное значение
     */
    public void resetDifficulty() {
        double initialValue = plugin.getConfig().getDouble("difficulty.initial-multiplier", 1.0);
        setDifficulty(initialValue);
        sessionStartTime = System.currentTimeMillis();
        
        if (plugin.getConfig().getBoolean("notifications.send-death-message", true)) {
            Bukkit.broadcast(
                net.kyori.adventure.text.Component.text("🔄 Сложность сброшена!")
                    .color(net.kyori.adventure.text.format.NamedTextColor.AQUA)
            );
        }

        notifyChange();
    }

    /**
     * Сохраняет данные сложности
     */
    public void saveDifficultyData() {
        plugin.getLogger().info("Данные сложности сохранены: " + currentMultiplier);
    }

    /**
     * Получает время с начала сессии в минутах
     */
    public long getSessionTimeMinutes() {
        return (System.currentTimeMillis() - sessionStartTime) / 60000;
    }

    public void setOnChange(Consumer<Double> onChange) {
        this.onChange = onChange;
    }

    private void notifyChange() {
        if (onChange != null) {
            onChange.accept(currentMultiplier);
        }
    }

    private void applyAttributeMultiplier(Mob mob, Attribute attribute, double multiplier) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance == null) {
            return;
        }

        double base = instance.getBaseValue();
        if (base > 0) {
            instance.setBaseValue(base * multiplier);
        }
    }
}
