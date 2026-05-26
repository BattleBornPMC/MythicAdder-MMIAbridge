package com.mythicadder;

import com.mythicadder.commands.MythicAdderCommand;
import com.mythicadder.hooks.ItemsAdderHook;
import com.mythicadder.hooks.MythicHook;
import com.mythicadder.listeners.CraftingListener;
import com.mythicadder.listeners.ReloadListener;
import com.mythicadder.recipe.CrossRecipe;
import com.mythicadder.recipe.RecipeManager;
import com.mythicadder.scanner.ItemsAdderScanner;
import com.mythicadder.scanner.MythicMobsScanner;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MythicAdder extends JavaPlugin {

    private MythicHook mythicHook;
    private ItemsAdderHook iaHook;
    private RecipeManager recipeManager;
    private ItemsAdderScanner iaScanner;
    private MythicMobsScanner mmScanner;
    private boolean debug;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("info.txt", false); // only writes on first start
        debug = getConfig().getBoolean("debug", false);

        boolean hasMythic = getServer().getPluginManager().isPluginEnabled("MythicMobs");
        boolean hasIA = getServer().getPluginManager().isPluginEnabled("ItemsAdder");

        if (hasMythic) {
            mythicHook = new MythicHook();
            getLogger().info("Hooked into MythicMobs.");
        } else {
            getLogger().warning("MythicMobs not found — MM recipes disabled.");
        }

        if (hasIA) {
            iaHook = new ItemsAdderHook();
            getLogger().info("Hooked into ItemsAdder.");
        } else {
            getLogger().warning("ItemsAdder not found — IA recipes disabled.");
        }

        recipeManager = new RecipeManager(this, mythicHook, iaHook);
        iaScanner = new ItemsAdderScanner(this);
        mmScanner = new MythicMobsScanner(this);

        // If ItemsAdder is present, it fires ItemsAdderLoadDataEvent after it fully loads,
        // which triggers our first scan. If not, scan immediately.
        if (!hasIA) rescan();

        getServer().getPluginManager().registerEvents(
                new CraftingListener(this, recipeManager, mythicHook, iaHook), this);

        if (hasMythic || hasIA) {
            getServer().getPluginManager().registerEvents(new ReloadListener(this), this);
        }

        MythicAdderCommand cmd = new MythicAdderCommand(this);
        var bukkitCmd = getCommand("mythicadder");
        if (bukkitCmd != null) {
            bukkitCmd.setExecutor(cmd);
            bukkitCmd.setTabCompleter(cmd);
        }

        getLogger().info("MMIAbridge enabled.");
    }

    public void rescan() {
        List<CrossRecipe> all = new ArrayList<>();

        if (iaHook != null) {
            File iaFolder = new File(getDataFolder().getParentFile(), "ItemsAdder");
            all.addAll(iaScanner.scan(iaFolder));
        }

        if (mythicHook != null) {
            File mmFolder = new File(getDataFolder().getParentFile(), "MythicMobs");
            all.addAll(mmScanner.scan(mmFolder));
        }

        recipeManager.setRecipes(all);
        getLogger().info("MMIAbridge loaded " + all.size() + " cross-plugin recipe(s).");
    }

    @Override
    public void onDisable() {
        getLogger().info("MMIAbridge disabled.");
    }

    public MythicHook getMythicHook() { return mythicHook; }
    public ItemsAdderHook getIaHook() { return iaHook; }
    public RecipeManager getRecipeManager() { return recipeManager; }
    public boolean isDebug() { return debug; }
    public void setDebug(boolean debug) { this.debug = debug; }
}
