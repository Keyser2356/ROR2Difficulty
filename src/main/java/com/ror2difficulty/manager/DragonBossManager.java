package com.ror2difficulty.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.boss.DragonBattle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class DragonBossManager {

    private final Plugin plugin;
    private final DifficultyManager difficultyManager;
    private BukkitTask attackTask;
    private BukkitTask voiceTask;
    private BukkitTask endermanPurgeTask;
    private EnderDragon currentDragon;
    private int currentPhase = 1;
    private int attackCycle = 0;
    private boolean isBattleActive = false;
    private boolean portalActivated = false;
    private final Random random = new Random();
    private final List<EnderCrystal> customCrystals = new ArrayList<>();

    public DragonBossManager(Plugin plugin, DifficultyManager difficultyManager) {
        this.plugin = plugin;
        this.difficultyManager = difficultyManager;
    }

    public void start() {
        if (attackTask != null) return;

        attackTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkForDragonBattle();
            }
        }.runTaskTimer(plugin, 20L, 100L);

        // Непрерывная очистка эндерменов во время боя с драконом
        endermanPurgeTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (isBattleActive) {
                    purgeEndermen();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // каждую секунду

        startVoiceLines();
    }

    public void stop() {
        if (attackTask != null) {
            attackTask.cancel();
            attackTask = null;
        }
        if (voiceTask != null) {
            voiceTask.cancel();
            voiceTask = null;
        }
        if (endermanPurgeTask != null) {
            endermanPurgeTask.cancel();
            endermanPurgeTask = null;
        }
        if (currentDragon != null && !currentDragon.isDead()) {
            currentDragon.remove();
        }
        customCrystals.clear();
        isBattleActive = false;
        portalActivated = false;
    }

    private void checkForDragonBattle() {
        if (!plugin.getConfig().getBoolean("dragon-boss.enabled", true)) {
            return;
        }
        
        double multiplier = difficultyManager.getCurrentMultiplier();
        double triggerDifficulty = plugin.getConfig().getDouble("dragon-boss.trigger-difficulty", 12.0);
        
        if (multiplier >= triggerDifficulty && !portalActivated) {
            activateEndPortal();
        }
        
        if (multiplier >= triggerDifficulty && portalActivated && !isBattleActive) {
            startDragonBattle();
        }
        
        if (isBattleActive && currentDragon != null && !currentDragon.isDead()) {
            updateDragonBattle();
        } else if (isBattleActive && (currentDragon == null || currentDragon.isDead())) {
            onDragonDefeated();
            isBattleActive = false;
            currentPhase = 1;
            attackCycle = 0;
        }
    }

    private void activateEndPortal() {
        World end = Bukkit.getWorld("world_the_end");
        if (end == null) return;

        portalActivated = true;
        Location spawnLoc = new Location(end, 0, 65, 0);

        // Мощный визуальный эффект активации
        end.spawnParticle(Particle.DRAGON_BREATH, spawnLoc, 300, 5, 3, 5, 0.3);
        end.spawnParticle(Particle.PORTAL, spawnLoc, 400, 5, 3, 5, 1.0);
        end.spawnParticle(Particle.EXPLOSION_EMITTER, spawnLoc, 10, 2, 2, 2, 0.1);
        end.spawnParticle(Particle.FIREWORK, spawnLoc, 200, 4, 3, 4, 0.5);
        
        end.playSound(spawnLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 5.0f, 0.6f);
        end.playSound(spawnLoc, Sound.ENTITY_ENDER_DRAGON_AMBIENT, 5.0f, 0.8f);
        end.playSound(spawnLoc, Sound.BLOCK_PORTAL_TRIGGER, 3.0f, 0.5f);
        end.playSound(spawnLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 1.2f);
        
        // Непрерывная анимация портала
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 200 || isBattleActive) {
                    cancel();
                    return;
                }
                
                if (ticks % 4 == 0) {
                    end.spawnParticle(Particle.DRAGON_BREATH, 
                        spawnLoc.clone().add(random.nextDouble() * 8 - 4, random.nextDouble() * 4, random.nextDouble() * 8 - 4),
                        15, 0.5, 0.5, 0.5, 0.05);
                    end.spawnParticle(Particle.PORTAL,
                        spawnLoc.clone().add(random.nextDouble() * 8 - 4, random.nextDouble() * 4, random.nextDouble() * 8 - 4),
                        20, 1, 1, 1, 0.3);
                }
                
                if (ticks % 40 == 0) {
                    end.playSound(spawnLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 3.0f, 0.7f + random.nextFloat() * 0.3f);
                }
                
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void startDragonBattle() {
        World end = Bukkit.getWorld("world_the_end");
        if (end == null) {
            return;
        }

        purgeEndermen();

        DragonBattle battle = end.getEnderDragonBattle();
        if (battle == null) {
            return;
        }

        EnderDragon existingDragon = battle.getEnderDragon();
        if (existingDragon != null && !existingDragon.isDead()) {
            enhanceDragon(existingDragon);
            currentDragon = existingDragon;
            isBattleActive = true;
            sendVoiceLine(getPhaseStartLines(1));
        } else {
            battle.initiateRespawn();
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    EnderDragon dragon = battle.getEnderDragon();
                    if (dragon != null) {
                        enhanceDragon(dragon);
                        currentDragon = dragon;
                        isBattleActive = true;
                        sendVoiceLine(getPhaseStartLines(1));
                    }
                }
            }.runTaskLater(plugin, 100L);
        }
    }

    private void enhanceDragon(EnderDragon dragon) {
        if (dragon == null) return;

        double baseHealth = plugin.getConfig().getDouble("dragon-boss.health", 500.0);
        dragon.setMaxHealth(baseHealth);
        dragon.setHealth(baseHealth);
        
        updateDragonPhase(1);
    }

    private void updateDragonBattle() {
        if (currentDragon == null || currentDragon.isDead()) {
            return;
        }

        double healthPercent = (currentDragon.getHealth() / currentDragon.getMaxHealth()) * 100.0;
        int newPhase = getPhaseByHealth(healthPercent);
        
        if (newPhase != currentPhase) {
            transitionToPhase(newPhase);
        }

        if (attackCycle % 15 == 0) {
            performSpecialAttack();
        }
        attackCycle++;
    }

    private int getPhaseByHealth(double healthPercent) {
        double phase3Threshold = plugin.getConfig().getDouble("dragon-boss.phase-three-threshold", 33.0);
        double phase2Threshold = plugin.getConfig().getDouble("dragon-boss.phase-two-threshold", 67.0);
        
        if (healthPercent <= phase3Threshold) return 3;
        if (healthPercent <= phase2Threshold) return 2;
        return 1;
    }

    private void transitionToPhase(int phase) {
        currentPhase = phase;
        updateDragonPhase(phase);
        sendVoiceLine(getPhaseStartLines(phase));
        if (currentDragon != null) {
            currentDragon.setHealth(currentDragon.getMaxHealth());
        }
        
        Location loc = currentDragon.getLocation();
        World world = loc.getWorld();
        
        if (phase == 2) {
            world.spawnParticle(Particle.PORTAL, loc, 200, 3, 3, 3, 0.5);
            world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 3.0f, 0.7f);
        } else if (phase == 3) {
            world.spawnParticle(Particle.EXPLOSION, loc, 100, 5, 5, 5, 0.5);
            world.spawnParticle(Particle.FIREWORK, loc, 300, 5, 5, 5, 0.5);
            world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_HURT, 5.0f, 0.5f);
            world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.8f);
        }
    }

    private void updateDragonPhase(int phase) {
        if (currentDragon == null) return;
        
        currentDragon.setCustomName(getPhaseDisplayName(phase));
        currentDragon.setGlowing(true);
        
        Attribute movementSpeed = RegistryAccess.registryAccess()
            .getRegistry(RegistryKey.ATTRIBUTE)
            .get(org.bukkit.NamespacedKey.minecraft("generic.movement_speed"));
        
        if (movementSpeed != null) {
            AttributeInstance speedAttr = currentDragon.getAttribute(movementSpeed);
            if (speedAttr != null) {
                double speedMult = phase == 3 ? 1.5 : (phase == 2 ? 1.3 : 1.0);
                speedAttr.setBaseValue(0.2 * speedMult);
            }
        }
    }

    private String getPhaseDisplayName(int phase) {
        double mult = difficultyManager.getCurrentMultiplier();
        int level = (int) Math.floor(mult);
        
        return switch (phase) {
            case 1 -> "§b§lCelestial Dragon [LVL " + level + "×]";
            case 2 -> "§5§lVoid Dragon [LVL " + level + "×]";
            case 3 -> "§c§lHAHAHAHA DRAGON [∞×]";
            default -> "Ender Dragon";
        };
    }

    private void performSpecialAttack() {
        if (currentDragon == null || currentDragon.isDead()) {
            return;
        }

        Location dragonLoc = currentDragon.getLocation();
        
        switch (currentPhase) {
            case 1 -> performPhase1Attack(dragonLoc);
            case 2 -> performPhase2Attack(dragonLoc);
            case 3 -> performPhase3Attack(dragonLoc);
        }
    }

    private void performPhase1Attack(Location origin) {
        int choice = random.nextInt(2);
        
        if (choice == 0) {
            teleportSlam(origin);
        } else {
            enderBreath(origin);
        }
    }

    private void performPhase2Attack(Location origin) {
        int choice = random.nextInt(3);
        
        if (choice == 0) {
            spawnVoidPools(origin);
        } else if (choice == 1) {
            spawnCorruptedMinions(origin);
        } else {
            spawnFireballs(origin);
        }
    }

    private void performPhase3Attack(Location origin) {
        int choice = random.nextInt(4);
        
        if (choice == 0) {
            realityBreak(origin);
        } else if (choice == 1) {
            meteorRain(origin);
        } else if (choice == 2) {
            spawnLightning();
        } else {
            spawnFireballs(origin);
        }
    }

    private void teleportSlam(Location origin) {
        Player target = nearestPlayer(origin);
        if (target != null) {
            Location tpLoc = target.getLocation().clone().add(0, 10, 0);
            currentDragon.teleport(tpLoc);
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    origin.getWorld().spawnParticle(Particle.EXPLOSION, target.getLocation(), 50, 5, 1, 5);
                    origin.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.0f);
                    
                    for (Player p : target.getWorld().getPlayers()) {
                        if (p.getLocation().distance(target.getLocation()) <= 5) {
                            p.damage(15.0);
                            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0));
                        }
                    }
                }
            }.runTaskLater(plugin, 20L);
        }
    }

    private void enderBreath(Location origin) {
        Player target = nearestPlayer(origin);
        if (target != null) {
            origin.getWorld().spawnParticle(Particle.DRAGON_BREATH, origin, 100, 0.5, 0.5, 0.5, 0.1);
            target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 2));
            target.damage(10.0);
        }
    }

    private void spawnVoidPools(Location origin) {
        for (int i = 0; i < 3; i++) {
            Location poolLoc = origin.clone().add(
                (random.nextDouble() - 0.5) * 20,
                -5,
                (random.nextDouble() - 0.5) * 20
            );
            
            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    if (ticks++ > 100) {
                        cancel();
                        return;
                    }
                    
                    poolLoc.getWorld().spawnParticle(Particle.PORTAL, poolLoc, 20, 2, 0.5, 2, 0.1);
                    
                    for (Player p : poolLoc.getWorld().getPlayers()) {
                        if (p.getLocation().distance(poolLoc) <= 3) {
                            p.damage(2.0);
                            p.setVelocity(poolLoc.toVector().subtract(p.getLocation().toVector()).normalize().multiply(0.3));
                        }
                    }
                }
            }.runTaskTimer(plugin, 0L, 10L);
        }
    }

    private void spawnCorruptedMinions(Location origin) {
        int count = 5 + random.nextInt(6);
        
        for (int i = 0; i < count; i++) {
            Location spawnLoc = origin.clone().add(
                (random.nextDouble() - 0.5) * 15,
                0,
                (random.nextDouble() - 0.5) * 15
            );
            
            if (random.nextBoolean()) {
                Shulker shulker = (Shulker) origin.getWorld().spawnEntity(spawnLoc, EntityType.SHULKER);
                shulker.setGlowing(true);
            } else {
                origin.getWorld().spawnEntity(spawnLoc, EntityType.ENDERMITE);
            }
        }
        
        sendVoiceLine(Arrays.asList("§5[Dragon] §fВаши миньоны? Они умрут первыми."));
    }

    private void realityBreak(Location origin) {
        int event = random.nextInt(3);
        
        if (event == 0) {
            meteorRain(origin);
        } else if (event == 1) {
            for (Player p : origin.getWorld().getPlayers()) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 3));
                p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 200, 2));
            }
            sendVoiceLine(Arrays.asList("§5[Dragon] §fВремя остановлено…"));
        } else {
            for (int i = 0; i < 10; i++) {
                Location mobLoc = origin.clone().add(
                    (random.nextDouble() - 0.5) * 30,
                    0,
                    (random.nextDouble() - 0.5) * 30
                );
                origin.getWorld().spawnEntity(mobLoc, EntityType.ENDERMAN);
            }
        }
    }

    private void meteorRain(Location origin) {
        for (int i = 0; i < 8; i++) {
            int finalI = i;
            new BukkitRunnable() {
                @Override
                public void run() {
                    Location meteorLoc = origin.clone().add(
                        (random.nextDouble() - 0.5) * 40,
                        50,
                        (random.nextDouble() - 0.5) * 40
                    );
                    
                    origin.getWorld().spawnParticle(Particle.FLAME, meteorLoc, 100, 1, 1, 1, 0.1);
                    origin.getWorld().createExplosion(meteorLoc.clone().add(0, -50, 0), 3.0f, false, false);
                }
            }.runTaskLater(plugin, finalI * 10L);
        }
        
        sendVoiceLine(Arrays.asList("§5[Dragon] §fМетеоры — мой смех."));
    }

    private void spawnFireballs(Location origin) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(origin.getWorld())) {
                org.bukkit.entity.Fireball fireball = origin.getWorld().spawn(
                    origin.clone().add(0, -5, 0),
                    org.bukkit.entity.Fireball.class
                );
                fireball.setDirection(player.getLocation().toVector().subtract(origin.toVector()).normalize());
                fireball.setYield(currentPhase >= 2 ? 3.0F : 2.0F);
            }
        }
    }

    private void spawnLightning() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (currentDragon != null && player.getWorld().equals(currentDragon.getWorld())) {
                Location playerLoc = player.getLocation();
                player.getWorld().strikeLightning(playerLoc);
                
                if (currentPhase >= 2) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            player.getWorld().strikeLightning(playerLoc.clone().add(
                                (Math.random() - 0.5) * 10,
                                0,
                                (Math.random() - 0.5) * 10
                            ));
                        }
                    }.runTaskLater(plugin, 10L);
                }
            }
        }
    }

    private Player nearestPlayer(Location loc) {
        Player nearest = null;
        double minDist = Double.MAX_VALUE;
        
        for (Player p : loc.getWorld().getPlayers()) {
            double dist = p.getLocation().distance(loc);
            if (dist < minDist) {
                minDist = dist;
                nearest = p;
            }
        }
        
        return nearest;
    }

    private void purgeEndermen() {
        World end = Bukkit.getWorld("world_the_end");
        if (end == null) return;
        for (Entity e : end.getEntities()) {
            if (e.getType() == EntityType.ENDERMAN) {
                e.remove();
            }
        }
    }

    private void startVoiceLines() {
        voiceTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (isBattleActive && random.nextDouble() < 0.3) {
                    sendVoiceLine(getRandomVoiceLines());
                }
            }
        }.runTaskTimer(plugin, 400L, 400L);
    }

    private void sendVoiceLine(List<String> lines) {
        if (lines.isEmpty()) return;
        String line = lines.get(random.nextInt(lines.size()));
        Bukkit.broadcastMessage(line);
    }

    private List<String> getRandomVoiceLines() {
        return Arrays.asList(
            "§5[Dragon] §fМелкие воришки… вы крадёте осколки моей тюрьмы.",
            "§5[Dragon] §fВаша жадность — ваша могила.",
            "§5[Dragon] §fЯ вижу ваши сердца. Они бьются так жалко.",
            "§5[Dragon] §fБегите. Всё равно не убежите.",
            "§5[Dragon] §fХа-ха-ха… вы думаете, что это победа?",
            "§5[Dragon] §fВаша броня — бумага. Ваши мечи — игрушки.",
            "§5[Dragon] §fЯ был здесь до вас. Я буду здесь после.",
            "§5[Dragon] §fСдавайтесь. Станьте частью хаоса."
        );
    }

    private List<String> getPhaseStartLines(int phase) {
        return switch (phase) {
            case 1 -> Arrays.asList(
                "§b[Dragon] §fПробуждение… наконец-то.",
                "§b[Dragon] §fМои кристаллы — не ваши трофеи.",
                "§b[Dragon] §fЛомайте их, если осмелитесь. Я всё равно возьму больше.",
                "§b[Dragon] §fCelestial ярость… почувствуйте вечность!"
            );
            case 2 -> Arrays.asList(
                "§5[Dragon] §fТеперь… тьма внутри вас.",
                "§5[Dragon] §fВаши души уже мои.",
                "§5[Dragon] §fVoid зовёт. Слышите шепот?",
                "§5[Dragon] §fКоррупция распространяется. Вы — следующий."
            );
            case 3 -> Arrays.asList(
                "§c[Dragon] §fHAHAHAHA… ВЕСЬ МИР В ОГНЕ!",
                "§c[Dragon] §fЭто конец. Ваш конец.",
                "§c[Dragon] §fКлоны? Реальность? Всё сломано!",
                "§c[Dragon] §fLoop за loop… вы никогда не уйдёте.",
                "§c[Dragon] §fМетеоры — мой смех.",
                "§c[Dragon] §fЯ — хаос. Вы — пыль."
            );
            default -> Arrays.asList();
        };
    }

    private void onDragonDefeated() {
        World end = Bukkit.getWorld("world_the_end");
        if (end != null) {
            Location center = new Location(end, 0, 70, 0);
            end.createExplosion(center, 8.0f, false, false);
            end.spawnParticle(Particle.EXPLOSION_EMITTER, center, 8);
            end.spawnParticle(Particle.DRAGON_BREATH, center, 500, 5, 3, 5, 0.2);
            end.spawnParticle(Particle.FIREWORK, center, 400, 6, 4, 6, 0.5);

            // Убиваем всех мобов в Энде
            for (Entity e : end.getEntities()) {
                if (e instanceof Player) continue;
                if (e instanceof EnderDragon) continue;
                e.remove();
            }
        }

        sendVoiceLine(Arrays.asList(
            "§5[Dragon] §f…ты… украл… мою вечность…",
            "§5[Dragon] §fХа… ха… ха…",
            "§5[Dragon] §fЦикл… сломан…",
            "§5[Dragon] §fНо хаос… вечен."
        ));
    }

    public boolean isBattleActive() {
        return isBattleActive;
    }

    public EnderDragon getCurrentDragon() {
        return currentDragon;
    }

    public Plugin getPlugin() {
        return plugin;
    }
}
