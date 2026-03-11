package com.ror2difficulty.manager;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DisplayManager {

    private final Plugin plugin;
    private final DifficultyManager difficultyManager;
    private final LocalizationManager localizationManager;
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final Map<UUID, Long> playerLastActionBarTime = new HashMap<>();
    private BukkitTask displayTask;
    private int hahaFrame = 0;

    public DisplayManager(Plugin plugin, DifficultyManager difficultyManager, LocalizationManager localizationManager) {
        this.plugin = plugin;
        this.difficultyManager = difficultyManager;
        this.localizationManager = localizationManager;
    }

    public void start() {
        if (displayTask != null) return;
        displayTask = new BukkitRunnable() {
            @Override
            public void run() {
                refreshNow();
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void stop() {
        if (displayTask != null) {
            displayTask.cancel();
            displayTask = null;
        }
        bossBars.values().forEach(BossBar::removeAll);
        bossBars.clear();
    }

    public void refreshNow() {
        double multiplier = difficultyManager.getCurrentMultiplier();
        double percentage = difficultyManager.getDifficultyPercentage();
        String session = formatSession(difficultyManager.getSessionTimeMinutes());
        int online = Bukkit.getOnlinePlayers().size();
        double toHaha = difficultyManager.getToHaha();

        java.util.Set<UUID> onlineIds = new java.util.HashSet<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            onlineIds.add(player.getUniqueId());
            String localeTag = localizationManager.resolveLocale(player.locale() != null ? player.locale().toString() : null);
            String stage = animatedStage(localeTag);

            updateBossBar(player, stage, percentage, localeTag);
            updateXpBar(player, multiplier);
            updateScoreboard(player, multiplier, percentage, session, online, stage, toHaha);
            
            if (plugin.getConfig().getBoolean("display.actionbar.enabled", true)) {
                sendActionBar(player, formatActionBar(stage, multiplier));
            }
        }

        cleanupBossBars(onlineIds);
    }

    public void sendActionBar(Player player, String message) {
        player.sendActionBar(message);
        playerLastActionBarTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private String formatActionBar(String stage, double multiplier) {
        String stageColor = getStageColor(multiplier);
        String multColor = getMultiplierColor(multiplier);
        
        return String.format("§d⚡ %s%s §7│ %s✦ §f%.2fx", 
            stageColor, 
            stage, 
            multColor, 
            multiplier
        );
    }

    private String getStageColor(double multiplier) {
        if (multiplier < 1.5) return "§a"; // Зелёный
        if (multiplier < 3.0) return "§2"; // Тёмно-зелёный
        if (multiplier < 4.5) return "§e"; // Жёлтый
        if (multiplier < 6.0) return "§6"; // Золотой
        if (multiplier < 8.0) return "§c"; // Красный
        if (multiplier < 9.0) return "§4"; // Тёмно-красный
        if (multiplier < 10.0) return "§5"; // Фиолетовый
        return "§d§l"; // Ярко-фиолетовый жирный для HAHAHAHA
    }

    private String getMultiplierColor(double multiplier) {
        if (multiplier < 3.0) return "§a";
        if (multiplier < 6.0) return "§e";
        if (multiplier < 9.0) return "§c";
        return "§d";
    }

    private void updateBossBar(Player player, String stage, double percentage, String localeTag) {
        if (!plugin.getConfig().getBoolean("display.bossbar", false)) {
            return;
        }

        BossBar bar = bossBars.computeIfAbsent(player.getUniqueId(), id -> plugin.getServer().createBossBar("", BarColor.PURPLE, BarStyle.SEGMENTED_12, BarFlag.DARKEN_SKY));

        bar.setTitle(localizationManager.getUI("stage_label", "Stage", localeTag) + ": " + stage);

        String colorName = plugin.getConfig().getString("display.bossbar.color", "PURPLE");
        String overlay = plugin.getConfig().getString("display.bossbar.overlay", "SEGMENTED_12");
        try {
            bar.setColor(BarColor.valueOf(colorName.toUpperCase()));
        } catch (IllegalArgumentException ignored) {
            bar.setColor(BarColor.PURPLE);
        }
        try {
            bar.setStyle(BarStyle.valueOf(overlay.toUpperCase()));
        } catch (IllegalArgumentException ignored) {
            bar.setStyle(BarStyle.SEGMENTED_12);
        }

        double progress = Math.min(1.0, Math.max(0.0, percentage / 100.0));
        bar.setProgress(progress);

        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
    }

    private void updateXpBar(Player player, double multiplier) {
        if (!plugin.getConfig().getBoolean("display.xpbar.enabled", true)) {
            return;
        }

        double factor = plugin.getConfig().getDouble("display.xpbar.level-factor", 5.0);
        int level = (int) Math.round(multiplier * factor);
        player.setLevel(level);
        player.setExp((float) Math.min(0.99, (multiplier % 1.0)));
    }

    private void updateScoreboard(Player player, double multiplier, double percentage, String session, int online, String stage, double toHaha) {
        if (!plugin.getConfig().getBoolean("display.scoreboard.enabled", true)) {
            return;
        }

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }

        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective(
            "ror2diff",
            Criteria.DUMMY,
            Component.text(translateColor(plugin.getConfig().getString("display.scoreboard.title", "ROR2 Difficulty")))
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        java.util.List<String> lines = plugin.getConfig().getStringList("display.scoreboard.lines");
        if (lines.isEmpty()) {
            lines = java.util.Arrays.asList(
                "§7Сложность: §f{multiplier}",
                "§7Прогресс: §f{percentage}%",
                "§7Сессия: §f{session}"
            );
        }

        int score = lines.size();
        int idx = 0;
        for (String raw : lines) {
            String line = translateColor(fillPlaceholders(raw, multiplier, percentage, session, online, stage, toHaha));
            line = line + ChatColor.values()[idx % ChatColor.values().length];
            idx++;
            objective.getScore(line.substring(0, Math.min(40, line.length()))).setScore(score--);
        }

        player.setScoreboard(board);
    }

    private String fillPlaceholders(String input, double multiplier, double percentage, String session, int online, String stage, double toHaha) {
        return input
            .replace("{multiplier}", String.format("%.2f", multiplier))
            .replace("{percentage}", String.format("%.1f", percentage))
            .replace("{session}", session)
            .replace("{online}", String.valueOf(online))
            .replace("{stage}", stage)
            .replace("{to_haha}", String.format("%.2f", Math.max(0, toHaha)));
    }

    private String translateColor(String text) {
        return ChatColor.translateAlternateColorCodes('§', ChatColor.translateAlternateColorCodes('&', text));
    }

    private String formatSession(long minutes) {
        long hours = minutes / 60;
        long mins = minutes % 60;
        return hours > 0 ? (hours + "ч " + mins + "м") : (mins + "м");
    }

    private String animatedStage(String localeTag) {
        String base = difficultyManager.getStageName(localeTag);
        String upper = base.toUpperCase(java.util.Locale.ROOT);
        if (!upper.contains("ХА") && !upper.contains("HA")) {
            return base;
        }
        
        String prefix = upper.contains("ХА") ? "ХА" : "HA";
        String[] frames = {
            prefix,
            prefix + prefix,
            prefix + prefix + prefix,
            prefix + prefix + prefix + prefix,
            prefix + prefix + prefix + prefix + prefix,
            prefix + prefix + prefix + prefix + prefix + prefix,
            prefix + prefix + prefix + prefix + prefix + prefix + prefix
        };
        String frame = frames[hahaFrame % frames.length];
        hahaFrame++;
        return frame;
    }

    private void cleanupBossBars(java.util.Set<UUID> online) {
        bossBars.entrySet().removeIf(entry -> {
            if (online.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().removeAll();
            return true;
        });
    }
}
