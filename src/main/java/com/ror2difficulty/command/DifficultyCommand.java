package com.ror2difficulty.command;

import com.ror2difficulty.manager.DifficultyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class DifficultyCommand implements CommandExecutor {

    private final DifficultyManager difficultyManager;

    public DifficultyCommand(DifficultyManager difficultyManager) {
        this.difficultyManager = difficultyManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("ror2difficulty.check")) {
            sender.sendMessage(
                Component.text("❌ У вас нет прав на эту команду!")
                    .color(NamedTextColor.RED)
            );
            return true;
        }

        double multiplier = difficultyManager.getCurrentMultiplier();
        double percentage = difficultyManager.getDifficultyPercentage();
        long sessionTime = difficultyManager.getSessionTimeMinutes();
        String localeTag = (sender instanceof Player player && player.locale() != null) ? player.locale().toString() : null;
        String stageName = difficultyManager.getStageName(localeTag);

        // Определяем цвет в зависимости от сложности
        NamedTextColor color = getColorByDifficulty(multiplier);

        sender.sendMessage(Component.empty());
        sender.sendMessage(
            Component.text("═══════════════════════════════════════")
                .color(NamedTextColor.GRAY)
        );
        sender.sendMessage(
            Component.text("📊 Статус сложности ROR2Difficulty")
                .color(NamedTextColor.YELLOW)
                .decorate(TextDecoration.BOLD)
        );
        sender.sendMessage(
            Component.text("═══════════════════════════════════════")
                .color(NamedTextColor.GRAY)
        );
        
        sender.sendMessage(
            Component.text("Стадия: ")
                .color(NamedTextColor.WHITE)
                .append(Component.text(stageName)
                    .color(color)
                    .decorate(TextDecoration.BOLD))
        );

        sender.sendMessage(
            Component.text("Множитель: ")
                .color(NamedTextColor.WHITE)
                .append(Component.text(String.format("%.2f", multiplier))
                    .color(color)
                    .decorate(TextDecoration.BOLD))
        );
        
        sender.sendMessage(
            Component.text("Прогресс: ")
                .color(NamedTextColor.WHITE)
                .append(Component.text(String.format("%.1f%%", percentage))
                    .color(color))
                .append(Component.text(" " + getProgressBar(percentage))
                    .color(color))
        );
        
        sender.sendMessage(
            Component.text("Время сессии: ")
                .color(NamedTextColor.WHITE)
                .append(Component.text(formatTime(sessionTime))
                    .color(NamedTextColor.AQUA))
        );
        
        sender.sendMessage(
            Component.text("═══════════════════════════════════════")
                .color(NamedTextColor.GRAY)
        );
        sender.sendMessage(Component.empty());

        return true;
    }

    /**
     * Получает цвет в зависимости от уровня сложности
     */
    private NamedTextColor getColorByDifficulty(double multiplier) {
        if (multiplier < 2.0) {
            return NamedTextColor.GREEN;
        } else if (multiplier < 4.0) {
            return NamedTextColor.YELLOW;
        } else if (multiplier < 6.0) {
            return NamedTextColor.GOLD;
        } else if (multiplier < 8.0) {
            return NamedTextColor.RED;
        } else {
            return NamedTextColor.DARK_RED;
        }
    }

    /**
     * Генерирует визуальную полоску прогресса
     */
    private String getProgressBar(double percentage) {
        int filled = (int) (percentage / 10);
        int empty = 10 - filled;
        return "█".repeat(Math.min(filled, 10)) + "░".repeat(Math.max(empty, 0));
    }

    /**
     * Форматирует время в минутах в строку
     */
    private String formatTime(long minutes) {
        long hours = minutes / 60;
        long mins = minutes % 60;
        
        if (hours == 0) {
            return mins + " мин.";
        } else {
            return hours + "ч " + mins + "мин.";
        }
    }
}
