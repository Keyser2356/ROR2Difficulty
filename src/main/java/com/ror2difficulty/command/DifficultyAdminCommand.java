package com.ror2difficulty.command;

import com.ror2difficulty.manager.DifficultyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.bukkit.plugin.Plugin;

public class DifficultyAdminCommand implements CommandExecutor {

    private final DifficultyManager difficultyManager;
    private final Plugin plugin;

    public DifficultyAdminCommand(DifficultyManager difficultyManager, Plugin plugin) {
        this.difficultyManager = difficultyManager;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("ror2difficulty.admin")) {
            sender.sendMessage(
                Component.text("❌ У вас нет прав на эту команду!")
                    .color(NamedTextColor.RED)
            );
            return true;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "set":
                return handleSet(sender, args);
            case "reset":
                return handleReset(sender);
            case "reload":
                return handleReload(sender);
            case "add":
                return handleAdd(sender, args);
            default:
                showHelp(sender);
                return true;
        }
    }

    /**
     * Установка точного значения сложности
     */
    private boolean handleSet(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(
                Component.text("❌ Использование: /difficultyadmin set <значение>")
                    .color(NamedTextColor.RED)
            );
            return true;
        }

        try {
            double value = Double.parseDouble(args[1]);
            double oldValue = difficultyManager.getCurrentMultiplier();
            difficultyManager.setDifficulty(value);
            
            sender.sendMessage(
                Component.text("✅ Сложность установлена: ")
                    .color(NamedTextColor.GREEN)
                    .append(Component.text(String.format("%.2f", oldValue))
                        .color(NamedTextColor.GRAY)
                        .decorate(TextDecoration.STRIKETHROUGH))
                    .append(Component.text(" → ")
                        .color(NamedTextColor.WHITE))
                    .append(Component.text(String.format("%.2f", value))
                        .color(NamedTextColor.YELLOW)
                        .decorate(TextDecoration.BOLD))
            );
            return true;
        } catch (NumberFormatException e) {
            sender.sendMessage(
                Component.text("❌ Значение должно быть числом!")
                    .color(NamedTextColor.RED)
            );
            return true;
        }
    }

    /**
     * Добавление сложности
     */
    private boolean handleAdd(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(
                Component.text("❌ Использование: /difficultyadmin add <значение>")
                    .color(NamedTextColor.RED)
            );
            return true;
        }

        try {
            double value = Double.parseDouble(args[1]);
            double oldValue = difficultyManager.getCurrentMultiplier();
            difficultyManager.addDifficulty(value);
            double newValue = difficultyManager.getCurrentMultiplier();
            
            String sign = value >= 0 ? "+" : "";
            sender.sendMessage(
                Component.text("✅ Сложность изменена: ")
                    .color(NamedTextColor.GREEN)
                    .append(Component.text(String.format("%.2f", oldValue))
                        .color(NamedTextColor.GRAY))
                    .append(Component.text(" → ")
                        .color(NamedTextColor.WHITE))
                    .append(Component.text(String.format("%.2f", newValue))
                        .color(NamedTextColor.YELLOW)
                        .decorate(TextDecoration.BOLD))
                    .append(Component.text(String.format(" (%s%.3f)", sign, value))
                        .color(value >= 0 ? NamedTextColor.GREEN : NamedTextColor.RED))
            );
            return true;
        } catch (NumberFormatException e) {
            sender.sendMessage(
                Component.text("❌ Значение должно быть числом!")
                    .color(NamedTextColor.RED)
            );
            return true;
        }
    }

    /**
     * Сброс сложности на начальное значение
     */
    private boolean handleReset(CommandSender sender) {
        difficultyManager.resetDifficulty();
        sender.sendMessage(
            Component.text("✅ Сложность была сброшена на начальное значение!")
                .color(NamedTextColor.GREEN)
        );
        return true;
    }

    /**
     * Перезагрузка конфига
     */
    private boolean handleReload(CommandSender sender) {
        plugin.reloadConfig();
        difficultyManager.initialize();
        sender.sendMessage(
            Component.text("✅ Конфиг перезагружен!")
                .color(NamedTextColor.GREEN)
        );
        return true;
    }

    /**
     * Показывает справку по командам
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(
            Component.text("╔════════════════════════════════════════╗")
                .color(NamedTextColor.GOLD)
        );
        sender.sendMessage(
            Component.text("║  ROR2Difficulty - Команды Администратора  ║")
                .color(NamedTextColor.GOLD)
        );
        sender.sendMessage(
            Component.text("╚════════════════════════════════════════╝")
                .color(NamedTextColor.GOLD)
        );
        sender.sendMessage(Component.empty());
        
        sender.sendMessage(
            Component.text("/difficultyadmin set <число>")
                .color(NamedTextColor.AQUA)
                .append(Component.text(" - Установить точное значение сложности")
                    .color(NamedTextColor.GRAY))
        );
        
        sender.sendMessage(
            Component.text("/difficultyadmin add <число>")
                .color(NamedTextColor.AQUA)
                .append(Component.text(" - Добавить/вычесть сложность")
                    .color(NamedTextColor.GRAY))
        );
        
        sender.sendMessage(
            Component.text("/difficultyadmin reset")
                .color(NamedTextColor.AQUA)
                .append(Component.text(" - Сбросить на начальное значение")
                    .color(NamedTextColor.GRAY))
        );
        
        sender.sendMessage(
            Component.text("/difficultyadmin reload")
                .color(NamedTextColor.AQUA)
                .append(Component.text(" - Перезагрузить конфиг")
                    .color(NamedTextColor.GRAY))
        );
        
        sender.sendMessage(Component.empty());
    }
}
