package com.mythicadder.commands;

import com.mythicadder.MythicAdder;
import com.mythicadder.recipe.CrossRecipe;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public class MythicAdderCommand implements CommandExecutor, TabCompleter {

    private final MythicAdder plugin;

    public MythicAdderCommand(MythicAdder plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mythicadder.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.rescan();
                sender.sendMessage("§aMMIAbridge rescanned. Loaded "
                        + plugin.getRecipeManager().getRecipes().size() + " cross-plugin recipe(s).");
            }
            case "list" -> {
                List<CrossRecipe> recipes = plugin.getRecipeManager().getRecipes();
                if (recipes.isEmpty()) {
                    sender.sendMessage("§eNo cross-plugin recipes loaded.");
                } else {
                    sender.sendMessage("§aLoaded cross-plugin recipes (" + recipes.size() + "):");
                    for (CrossRecipe r : recipes) {
                        sender.sendMessage("§7 - §f" + r.getName()
                                + " §7[" + r.getRecipeType().name().toLowerCase() + "] -> "
                                + r.getResult().getType().name().toLowerCase()
                                + ":" + r.getResult().getId()
                                + " x" + r.getResultAmount());
                    }
                }
            }
            case "debug" -> {
                plugin.setDebug(!plugin.isDebug());
                sender.sendMessage("§aDebug mode: " + (plugin.isDebug() ? "§2ON" : "§cOFF"));
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6MMIAbridge §7commands:");
        sender.sendMessage("§f/mythicadder reload §7- Rescan IA and MM config files");
        sender.sendMessage("§f/mythicadder list §7- List loaded cross-plugin recipes");
        sender.sendMessage("§f/mythicadder debug §7- Toggle debug logging");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("reload", "list", "debug").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
