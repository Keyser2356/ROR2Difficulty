package com.ror2difficulty.manager;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Wither;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MobBehaviorManager {

    private final Plugin plugin;
    private final DifficultyManager difficultyManager;
    private BukkitTask behaviorTask;
    private final Map<UUID, Long> spiderCooldownMs = new HashMap<>();

    public MobBehaviorManager(Plugin plugin, DifficultyManager difficultyManager) {
        this.plugin = plugin;
        this.difficultyManager = difficultyManager;
    }

    public void start() {
        // Каждые 5 тиков проверяем поведение мобов
        behaviorTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateMobBehaviors, 20L, 5L);
    }

    public void stop() {
        if (behaviorTask != null) {
            behaviorTask.cancel();
            behaviorTask = null;
        }
    }

    private void updateMobBehaviors() {
        double multiplier = difficultyManager.getCurrentMultiplier();
        String stage = difficultyManager.getStageKey();

        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                if (!(entity instanceof Mob mob)) {
                    continue;
                }

                EntityType type = mob.getType();

                // STAGE 1-2: Очень слабые враги, без особых умений
                if (multiplier < 3.0) {
                    continue;
                }
                
                // STAGE 3: Враги начинают показывать умения
                // Пауки: плюют ядом
                if (type == EntityType.SPIDER || type == EntityType.CAVE_SPIDER) {
                    handleSpiderBehavior((Spider) mob, multiplier, stage);
                }
                
                // STAGE 4: Особые враги активнее
                // Крипер: большие взрывы
                if (type == EntityType.CREEPER) {
                    handleCreeperBehavior((Creeper) mob, multiplier, stage);
                }
                // Скелеты: луки работают активнее
                else if (type == EntityType.SKELETON || type == EntityType.STRAY || type == EntityType.WITHER_SKELETON) {
                    handleSkeletonBehavior((Skeleton) mob, multiplier, stage);
                }
                
                // STAGE 6+: Боссы становятся опасными
                // Боссы: специальное поведение
                else if (type == EntityType.ENDER_DRAGON) {
                    handleEnderDragonBehavior((EnderDragon) mob, multiplier, stage);
                } else if (type == EntityType.WITHER) {
                    handleWitherBehavior((Wither) mob, multiplier, stage);
                }
            }
        }
    }

    private void handleSpiderBehavior(Spider spider, double multiplier, String stage) {
        // STAGE 3 (3.0-4.5): Редкий яд
        // STAGE 4 (4.5-6.0): Частый яд
        // STAGE 5+ (6.0+): Очень частый яд с урогом
        
        if (spider.getTarget() == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long last = spiderCooldownMs.getOrDefault(spider.getUniqueId(), 0L);
        
        long cooldownMs = switch (stage) {
            case "very_easy", "easy" -> 3000L;       // Почти не атакуют
            case "medium" -> 2500L;                  // Редко
            case "hard" -> 2000L;                    // Нормально
            case "very_hard" -> 1500L;               // Часто
            case "insane", "impossible", "haha" -> 1000L; // Очень часто
            default -> 2000L;
        };
        
        if (now - last < cooldownMs) {
            return;
        }

        double attackChance = switch (stage) {
            case "very_easy", "easy" -> 0.1;         // 10% шанс
            case "medium" -> 0.25;                   // 25% шанс
            case "hard" -> 0.50;                     // 50% шанс
            case "very_hard" -> 0.75;                // 75% шанс
            case "insane", "impossible", "haha" -> 1.0; // 100% шанс
            default -> 0.5;
        };
        
        if (Math.random() > attackChance) {
            return;
        }

        // Спауним партиклы яда
        org.bukkit.Location loc = spider.getEyeLocation();
        spider.getWorld().spawnParticle(Particle.EFFECT, loc, 10, 0.5, 0.5, 0.5, 0.1);
        spider.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, loc, 5, 0.3, 0.3, 0.3, 0.05);

        // Урон зависит от стадии
        org.bukkit.entity.LivingEntity target = spider.getTarget();
        double baseDamage = switch (stage) {
            case "very_easy" -> 0.5;
            case "easy" -> 1.0;
            case "medium" -> 1.5;
            case "hard" -> 2.0;
            case "very_hard" -> 3.0;
            case "insane" -> 4.0;
            case "impossible" -> 5.0;
            case "haha" -> 7.0;
            default -> 1.5;
        };
        
        target.damage(capPlayerDamage(baseDamage));
        spiderCooldownMs.put(spider.getUniqueId(), now);
    }

    private void handleCreeperBehavior(Creeper creeper, double multiplier, String stage) {
        // STAGE 4+: Крипер быстрее взрывается
        if (stage.equals("very_easy") || stage.equals("easy")) {
            return;
        }

        int fuseTicks = creeper.getFuseTicks();
        if (fuseTicks > 0 && fuseTicks < 10) {
            int speedup = switch (stage) {
                case "medium" -> 1;
                case "hard" -> 2;
                case "very_hard" -> 3;
                case "insane", "impossible", "haha" -> 4;
                default -> 1;
            };
            
            creeper.setFuseTicks(Math.max(0, fuseTicks - speedup));
            creeper.getWorld().spawnParticle(Particle.FLAME, creeper.getLocation(), 5, 0.2, 0.2, 0.2, 0.1);
        }
    }

    private void handleSkeletonBehavior(Skeleton skeleton, double multiplier, String stage) {
        // STAGE 5+: Скелеты стреляют быстрее
        if (stage.equals("very_easy") || stage.equals("easy") || stage.equals("medium") || stage.equals("hard")) {
            return;
        }

        if (skeleton.getTarget() != null && Math.random() > 0.7) {
            skeleton.setInvulnerable(false);
        }
    }

    private void handleEnderDragonBehavior(EnderDragon dragon, double multiplier, String stage) {
        // STAGE 6+: Драконовые боссовые атаки
        if (stage.equals("very_easy") || stage.equals("easy") || stage.equals("medium") || stage.equals("hard") || stage.equals("very_hard")) {
            return;
        }

        org.bukkit.Location loc = dragon.getLocation();
        dragon.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc, 10, 3, 3, 3, 0.1);

        double damage = switch (stage) {
            case "insane" -> 5.0;
            case "impossible" -> 7.0;
            case "haha" -> 10.0;
            default -> 3.0;
        };

        for (org.bukkit.entity.Player player : dragon.getWorld().getPlayers()) {
            double dist = dragon.getLocation().distance(player.getLocation());
            if (dist < 15.0) {
                player.damage(capPlayerDamage(damage));
            }
        }
    }

    private void handleWitherBehavior(Wither wither, double multiplier, String stage) {
        // STAGE 6+: Виземоне атаки с слабостью
        if (stage.equals("very_easy") || stage.equals("easy") || stage.equals("medium") || stage.equals("hard") || stage.equals("very_hard")) {
            return;
        }

        org.bukkit.Location loc = wither.getLocation();
        wither.getWorld().spawnParticle(Particle.SMOKE, loc, 15, 2, 2, 2, 0.1);

        double damage = switch (stage) {
            case "insane" -> 4.0;
            case "impossible" -> 6.0;
            case "haha" -> 8.0;
            default -> 2.5;
        };

        int witherDuration = switch (stage) {
            case "insane" -> 60;
            case "impossible" -> 120;
            case "haha" -> 180;
            default -> 30;
        };

        for (org.bukkit.entity.Player player : wither.getWorld().getPlayers()) {
            double dist = wither.getLocation().distance(player.getLocation());
            if (dist < 12.0) {
                player.damage(capPlayerDamage(damage));
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.WITHER,
                    witherDuration,
                    1,
                    false,
                    false
                ));
            }
        }
    }

    private double capPlayerDamage(double raw) {
        double hardCap = plugin.getConfig().getDouble("effects.player-damage-cap", 16.0);
        if (hardCap > 0) {
            return Math.min(raw, hardCap);
        }
        return raw;
    }
}
