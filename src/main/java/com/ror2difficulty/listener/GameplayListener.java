package com.ror2difficulty.listener;

import com.ror2difficulty.manager.DifficultyManager;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.EnumSet;
import java.util.Random;

public class GameplayListener implements Listener {

    private static final EnumSet<EntityType> IGNORED_MOBS = EnumSet.of(EntityType.ARMOR_STAND, EntityType.ALLAY, EntityType.VILLAGER);

    private final Plugin plugin;
    private final DifficultyManager difficultyManager;
    private final Random random = new Random();

    public GameplayListener(Plugin plugin, DifficultyManager difficultyManager) {
        this.plugin = plugin;
        this.difficultyManager = difficultyManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (IGNORED_MOBS.contains(mob.getType())) {
            return;
        }
        
        // Пропускаем дракона - он управляется DragonBossManager
        if (mob.getType() == org.bukkit.entity.EntityType.ENDER_DRAGON) {
            return;
        }

        // Применяем базовые эффекты сложности (здоровье, урон, скорость)
        difficultyManager.applyDifficultyEffects(mob);

        // На 10+ сложности враждебные неприученные мобы становятся агрессивными к игрокам
        if (difficultyManager.getCurrentMultiplier() >= 10.0 && isHostileMob(mob)) {
            if (!(mob instanceof Tameable tame && tame.isTamed())) {
                Player target = nearestPlayer(mob);
                if (target != null) {
                    mob.setTarget(target);
                }
            }
        }

        // Экипировка мобов
        if (plugin.getConfig().getBoolean("gameplay.mob-gear.enabled", true)) {
            maybeGiveGear(mob);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMobDamagePlayer(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Mob) || !(event.getEntity() instanceof Player)) {
            return;
        }

        double base = event.getDamage();
        double dmgMult = 1.0 + (plugin.getConfig().getDouble("effects.mob-damage-multiplier", 0.05) * (difficultyManager.getCurrentMultiplier() - 1.0));
        double scaled = base * dmgMult;

        double maxMult = plugin.getConfig().getDouble("effects.max-damage-multiplier", 2.0);
        scaled = Math.min(scaled, base * maxMult);

        double hardCap = plugin.getConfig().getDouble("effects.player-damage-cap", 16.0);
        if (hardCap > 0) {
            scaled = Math.min(scaled, hardCap);
        }

        event.setDamage(scaled);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEnvironmentalDamage(EntityDamageEvent event) {
        // Урон от падения
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            if (!plugin.getConfig().getBoolean("gameplay.fall-damage.enabled", true)) {
                return;
            }
            double mult = plugin.getConfig().getDouble("gameplay.fall-damage.multiplier", 1.25);
            event.setDamage(event.getDamage() * mult);
            return;
        }
        
        // На сложности 6+ события (молния, эффекты шторма) чисто визуальные
        if (difficultyManager.getCurrentMultiplier() >= 6.0) {
            if (event.getCause() == EntityDamageEvent.DamageCause.LIGHTNING ||
                event.getCause() == EntityDamageEvent.DamageCause.WITHER ||
                event.getCause() == EntityDamageEvent.DamageCause.POISON) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!plugin.getConfig().getBoolean("gameplay.hunger.enabled", true)) {
            return;
        }

        int current = player.getFoodLevel();
        int newLevel = event.getFoodLevel();
        if (newLevel < current) {
            double mult = plugin.getConfig().getDouble("gameplay.hunger.exhaustion-multiplier", 1.3);
            int delta = current - newLevel;
            int adjusted = current - (int) Math.ceil(delta * mult);
            event.setFoodLevel(Math.max(0, adjusted));
        }
    }

    private void maybeGiveGear(Mob mob) {
        double base = plugin.getConfig().getDouble("gameplay.mob-gear.base-chance", 0.05);
        double per = plugin.getConfig().getDouble("gameplay.mob-gear.per-multiplier", 0.05);
        double chance = Math.min(0.95, base + per * Math.max(0, difficultyManager.getCurrentMultiplier() - 1.0));

        if (random.nextDouble() > chance) {
            return;
        }

        String mobType = mob.getType().name();
        String path = "gameplay.mob-gear.mobs." + mobType;
        
        if (!plugin.getConfig().contains(path)) {
            // Используем умолчание по классификации
            GearTier tier = pickTier();
            EntityEquipment eq = mob.getEquipment();
            if (eq == null) return;

            eq.setHelmet(new ItemStack(tier.helmet));
            eq.setChestplate(new ItemStack(tier.chest));
            eq.setLeggings(new ItemStack(tier.legs));
            eq.setBoots(new ItemStack(tier.boots));
            eq.setItemInMainHand(createWeapon(tier.weapon));

            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setLeggingsDropChance(0f);
            eq.setBootsDropChance(0f);
            eq.setItemInMainHandDropChance(0f);
            return;
        }

        org.bukkit.configuration.ConfigurationSection mobConfig = plugin.getConfig().getConfigurationSection(path);
        EntityEquipment eq = mob.getEquipment();
        if (eq == null || mobConfig == null) {
            return;
        }

        // Загружаем конфиг для этого моба
        String helmet = mobConfig.getString("helmet", "");
        String chest = mobConfig.getString("chestplate", "");
        String legs = mobConfig.getString("leggings", "");
        String boots = mobConfig.getString("boots", "");
        String weapon = mobConfig.getString("weapon", "");

        if (!helmet.isEmpty() && !"null".equalsIgnoreCase(helmet)) {
            eq.setHelmet(new ItemStack(Material.valueOf(helmet)));
        }
        if (!chest.isEmpty() && !"null".equalsIgnoreCase(chest)) {
            eq.setChestplate(new ItemStack(Material.valueOf(chest)));
        }
        if (!legs.isEmpty() && !"null".equalsIgnoreCase(legs)) {
            eq.setLeggings(new ItemStack(Material.valueOf(legs)));
        }
        if (!boots.isEmpty() && !"null".equalsIgnoreCase(boots)) {
            eq.setBoots(new ItemStack(Material.valueOf(boots)));
        }

        if (!weapon.isEmpty() && !"null".equalsIgnoreCase(weapon)) {
            eq.setItemInMainHand(createWeapon(Material.valueOf(weapon)));
        }

        // Ничего не дропаем
        eq.setHelmetDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setBootsDropChance(0f);
        eq.setItemInMainHandDropChance(0f);
    }

    private GearTier pickTier() {
        double m = difficultyManager.getCurrentMultiplier();
        if (m >= 7) {
            return GearTier.DIAMOND;
        } else if (m >= 4) {
            return GearTier.IRON;
        } else if (m >= 2) {
            return GearTier.CHAIN;
        } else {
            return GearTier.LEATHER;
        }
    }

    private ItemStack createWeapon(Material material) {
        ItemStack stack = new ItemStack(material);
        int levelPer = plugin.getConfig().getInt("gameplay.mob-gear.enchant-level-per-multiplier", 1);
        int level = Math.max(1, (int) Math.round(difficultyManager.getCurrentMultiplier() * levelPer));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.SHARPNESS, Math.min(level, 5), true);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private Player nearestPlayer(Mob mob) {
        double best = Double.MAX_VALUE;
        Player bestPlayer = null;
        for (Player p : mob.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(mob.getLocation());
            if (d < best) {
                best = d;
                bestPlayer = p;
            }
        }
        return bestPlayer;
    }

    private boolean isHostileMob(Mob mob) {
        EntityType type = mob.getType();
        return type != EntityType.VILLAGER &&
               type != EntityType.WANDERING_TRADER &&
               type != EntityType.IRON_GOLEM &&
               type != EntityType.SNOW_GOLEM &&
               type != EntityType.AXOLOTL &&
               type != EntityType.FROG &&
               type != EntityType.ALLAY &&
               type != EntityType.CAT &&
               type != EntityType.HORSE &&
               type != EntityType.DONKEY &&
               type != EntityType.LLAMA &&
               type != EntityType.MULE &&
               type != EntityType.SHEEP &&
               type != EntityType.COW &&
               type != EntityType.PIG &&
               type != EntityType.CHICKEN;
    }

    private enum GearTier {
        LEATHER(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS, Material.WOODEN_SWORD),
        CHAIN(Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS, Material.STONE_SWORD),
        IRON(Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS, Material.IRON_SWORD),
        DIAMOND(Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS, Material.DIAMOND_SWORD);

        final Material helmet;
        final Material chest;
        final Material legs;
        final Material boots;
        final Material weapon;

        GearTier(Material helmet, Material chest, Material legs, Material boots, Material weapon) {
            this.helmet = helmet;
            this.chest = chest;
            this.legs = legs;
            this.boots = boots;
            this.weapon = weapon;
        }
    }
}
