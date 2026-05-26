package com.mythicadder.listeners;

import com.mythicadder.MythicAdder;
import com.mythicadder.hooks.ItemsAdderHook;
import com.mythicadder.hooks.MythicHook;
import com.mythicadder.recipe.CrossRecipe;
import com.mythicadder.recipe.RecipeManager;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

public class CraftingListener implements Listener {

    private final MythicAdder plugin;
    private final RecipeManager recipeManager;
    private final MythicHook mythic;
    private final ItemsAdderHook ia;

    public CraftingListener(MythicAdder plugin, RecipeManager recipeManager,
                            MythicHook mythic, ItemsAdderHook ia) {
        this.plugin = plugin;
        this.recipeManager = recipeManager;
        this.mythic = mythic;
        this.ia = ia;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareItemCraftEvent event) {
        CraftingInventory inv = event.getInventory();
        ItemStack[] matrix = inv.getMatrix();

        // Fast path: vanilla items carry no PDC data — skip entirely if nothing custom is present
        if (!hasCustomItems(matrix)) return;

        if (plugin.isDebug()) {
            String player = event.getViewers().isEmpty() ? "?" : event.getViewers().get(0).getName();
            logGrid(matrix, player);
        }

        CrossRecipe match = recipeManager.findMatch(matrix);
        if (match == null) {
            if (plugin.isDebug()) {
                for (CrossRecipe recipe : recipeManager.getRecipes()) {
                    plugin.getLogger().info("[Debug] No match — " + recipe.getName()
                            + ": " + recipe.debugFailReason(matrix, mythic, ia));
                }
            }
            return;
        }

        ItemStack result = match.buildResult(mythic, ia);
        if (result == null) {
            if (plugin.isDebug())
                plugin.getLogger().info("[Debug] Recipe '" + match.getName() + "' matched but result item could not be built (check MM/IA item ID).");
            return;
        }

        inv.setResult(result);

        if (plugin.isDebug()) {
            String player = event.getViewers().isEmpty() ? "?" : event.getViewers().get(0).getName();
            plugin.getLogger().info("[Debug] Recipe '" + match.getName() + "' matched for " + player
                    + " -> result: " + result.getType() + " x" + result.getAmount());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        CraftingInventory inv = event.getInventory();
        if (!hasCustomItems(inv.getMatrix())) return;
        CrossRecipe match = recipeManager.findMatch(inv.getMatrix());
        if (match == null) return;

        ItemStack result = match.buildResult(mythic, ia);
        if (result == null) {
            event.setCancelled(true);
            return;
        }

        inv.setResult(result);

        if (plugin.isDebug())
            plugin.getLogger().info("[Debug] Crafted recipe '" + match.getName()
                    + "' by " + event.getWhoClicked().getName());
    }

    private void logGrid(ItemStack[] matrix, String player) {
        StringBuilder sb = new StringBuilder("[Debug] Grid for ").append(player).append(": [");
        boolean hasAnything = false;

        for (int i = 0; i < matrix.length; i++) {
            ItemStack stack = matrix[i];
            if (stack == null || stack.getType() == Material.AIR) {
                sb.append("_");
            } else {
                hasAnything = true;
                String mmName = mythic != null ? mythic.getMythicName(stack).orElse(null) : null;
                String iaName = ia != null ? ia.getItemsAdderName(stack) : null;
                if (mmName != null)      sb.append("MM:").append(mmName);
                else if (iaName != null) sb.append("IA:").append(iaName);
                else                     sb.append(stack.getType().name());
            }
            if (i < matrix.length - 1) sb.append(", ");
        }

        sb.append("] | ").append(recipeManager.getRecipes().size()).append(" recipe(s) loaded");

        if (hasAnything) plugin.getLogger().info(sb.toString());
    }

    /** Returns true if any slot carries PersistentDataContainer data (i.e. is a custom item). */
    private boolean hasCustomItems(ItemStack[] matrix) {
        for (ItemStack s : matrix) {
            if (s == null || s.getType() == Material.AIR || !s.hasItemMeta()) continue;
            if (!s.getItemMeta().getPersistentDataContainer().getKeys().isEmpty()) return true;
        }
        return false;
    }
}
