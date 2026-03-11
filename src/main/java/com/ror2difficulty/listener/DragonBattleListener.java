package com.ror2difficulty.listener;

import com.ror2difficulty.manager.DragonBossManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class DragonBattleListener implements Listener {

    private final DragonBossManager dragonBossManager;

    public DragonBattleListener(DragonBossManager dragonBossManager) {
        this.dragonBossManager = dragonBossManager;
    }

    @EventHandler
    public void onDragonDeath(EntityDeathEvent event) {
        if (event.getEntityType() != EntityType.ENDER_DRAGON) {
            return;
        }

        EnderDragon dragon = (EnderDragon) event.getEntity();
        
        // Проверяем, что это наш босс-дракон
        if (dragonBossManager.isBattleActive() && 
            dragonBossManager.getCurrentDragon() != null && 
            dragonBossManager.getCurrentDragon().equals(dragon)) {
            
            // Уведомление о победе
            Bukkit.broadcast(
                net.kyori.adventure.text.Component.text("⚔ BOSS DEFEATED! ⚔")
                    .color(net.kyori.adventure.text.format.NamedTextColor.GOLD)
                    .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)
            );
            
            Bukkit.broadcast(
                net.kyori.adventure.text.Component.text("Ender Dragon has been slain!")
                    .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW)
            );
            
            // Убираем стандартные дропы и яйцо
            event.getDrops().clear();
            event.setDroppedExp(event.getDroppedExp() * 3);

            dragon.getWorld().dropItemNaturally(
                dragon.getLocation(),
                new org.bukkit.inventory.ItemStack(org.bukkit.Material.ELYTRA)
            );

            dragon.getWorld().dropItemNaturally(
                dragon.getLocation(),
                new org.bukkit.inventory.ItemStack(org.bukkit.Material.DRAGON_HEAD)
            );

            // Чистим яйцо на гнезде, если появилось
            Bukkit.getScheduler().runTaskLater(dragonBossManager.getPlugin(), () -> {
                int radius = 10;
                for (int x = -radius; x <= radius; x++) {
                    for (int y = 50; y <= 100; y++) {
                        for (int z = -radius; z <= radius; z++) {
                            org.bukkit.block.Block b = dragon.getWorld().getBlockAt(x, y, z);
                            if (b.getType() == org.bukkit.Material.DRAGON_EGG) {
                                b.setType(org.bukkit.Material.AIR);
                            }
                        }
                    }
                }
            }, 40L);
        }
    }
}
