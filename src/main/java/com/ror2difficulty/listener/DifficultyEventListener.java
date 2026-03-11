package com.ror2difficulty.listener;

import com.ror2difficulty.manager.DifficultyManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.Plugin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.Material;

import java.util.Random;

public class DifficultyEventListener implements Listener {

    private final DifficultyManager difficultyManager;
    private final Plugin plugin;
    private final Random random = new Random();

    public DifficultyEventListener(DifficultyManager difficultyManager, Plugin plugin) {
        this.difficultyManager = difficultyManager;
        this.plugin = plugin;
    }

    /**
     * Обработка смерти игрока
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        
        plugin.getLogger().info(player.getName() + " умер. Сложность уменьшается...");
        difficultyManager.onPlayerDeath();
    }

    /**
     * Обработка убийства сущности (моба)
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        
        // Пропускаем игроков
        if (entity instanceof Player) {
            return;
        }

        // Проверяем, убил ли игрок этого моба
        if (entity.getLastDamageCause() == null) {
            return;
        }

        // Получаем имя типа моба
        String mobType = entity.getType().name();
        
        difficultyManager.onMobKilled(mobType);
        
        // Увеличиваем редкость дропа в зависимости от сложности
        enhanceDropRarity(event);
    }
    
    /**
     * Улучшает редкость дропа мобов на основе текущей сложности
     */
    private void enhanceDropRarity(EntityDeathEvent event) {
        // Пропускаем если это не моб
        if (!(event.getEntity() instanceof Mob)) {
            return;
        }
        
        double multiplier = difficultyManager.getCurrentMultiplier();
        
        // На сложности < 2.0 не улучшаем дроп
        if (multiplier < 2.0) {
            return;
        }
        
        // Вероятность улучшения предметов
        double enchantChance = Math.min(0.9, 0.05 + (multiplier - 1.0) * 0.08);
        
        // Улучшаем каждый предмет
        for (ItemStack item : event.getDrops()) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            
            // Пропускаем предметы которые нельзя enchant
            if (!item.getType().toString().endsWith("_HELMET") &&
                !item.getType().toString().endsWith("_CHESTPLATE") &&
                !item.getType().toString().endsWith("_LEGGINGS") &&
                !item.getType().toString().endsWith("_BOOTS") &&
                !item.getType().toString().endsWith("_AXE") &&
                !item.getType().toString().endsWith("_SWORD") &&
                !item.getType().toString().endsWith("_PICKAXE") &&
                !item.getType().toString().endsWith("_SHOVEL") &&
                !item.getType().toString().endsWith("_HOE") &&
                !item.getType().toString().endsWith("_BOW") &&
                item.getType() != Material.TRIDENT) {
                continue;
            }
            
            // Добавляем случайный enchantment если повезёт
            if (random.nextDouble() < enchantChance) {
                addRandomEnchantment(item, (int) Math.ceil(multiplier / 3.0));
            }
            
            // На сложности 6+ удваиваем редкие предметы
            if (multiplier >= 6.0 && isRareItem(item)) {
                event.getDrops().add(item.clone());
            }
        }
        
        // На сложности 8+ добавляем редкие вещества
        if (multiplier >= 8.0) {
            addBonusLoot(event, multiplier);
        }
    }
    
    /**
     * Добавляет случайный enchantment к предмету
     */
    private void addRandomEnchantment(ItemStack item, int maxLevel) {
        if (maxLevel < 1) return;
        
        Enchantment[] enchantments = {
            Enchantment.PROTECTION,
            Enchantment.SHARPNESS,
            Enchantment.EFFICIENCY,
            Enchantment.UNBREAKING,
            Enchantment.FORTUNE,
            Enchantment.LOOTING,
            Enchantment.KNOCKBACK,
            Enchantment.FIRE_ASPECT,
            Enchantment.POWER,
            Enchantment.PUNCH
        };
        
        Enchantment enchant = enchantments[random.nextInt(enchantments.length)];
        int level = Math.min(enchant.getMaxLevel(), maxLevel + random.nextInt(2));
        
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            try {
                meta.addEnchant(enchant, level, true);
                item.setItemMeta(meta);
            } catch (Exception ignored) {
                // Игнорируем ошибки несовместимости
            }
        }
    }
    
    /**
     * Проверяет является ли предмет редким
     */
    private boolean isRareItem(ItemStack item) {
        String name = item.getType().name();
        return name.contains("DIAMOND") || 
               name.contains("NETHERITE") || 
               name.contains("EMERALD") ||
               name.contains("GOLDEN") ||
               item.getType() == Material.TRIDENT;
    }
    
    /**
     * Добавляет бонусный лут на высоких сложностях
     */
    private void addBonusLoot(EntityDeathEvent event, double multiplier) {
        Material[] bonusLoot = {
            Material.DIAMOND,
            Material.EMERALD,
            Material.NETHERITE_SCRAP,
            Material.GOLD_INGOT,
            Material.AMETHYST_SHARD
        };
        
        // На сложности 8-9 вероятность 30%, 10+ вероятность 50%
        double chance = multiplier >= 10.0 ? 0.5 : 0.3;
        
        if (random.nextDouble() < chance) {
            Material bonus = bonusLoot[random.nextInt(bonusLoot.length)];
            int amount = 1 + random.nextInt((int) Math.ceil(multiplier / 4.0));
            event.getDrops().add(new ItemStack(bonus, amount));
        }
    }
}
