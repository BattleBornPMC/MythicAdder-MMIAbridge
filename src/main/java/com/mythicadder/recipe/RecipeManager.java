package com.mythicadder.recipe;

import com.mythicadder.hooks.ItemsAdderHook;
import com.mythicadder.hooks.MythicHook;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RecipeManager {

    private final MythicHook mythic;
    private final ItemsAdderHook ia;
    private final List<CrossRecipe> recipes = new ArrayList<>();

    public RecipeManager(MythicHook mythic, ItemsAdderHook ia) {
        this.mythic = mythic;
        this.ia = ia;
    }

    public void setRecipes(List<CrossRecipe> incoming) {
        recipes.clear();
        recipes.addAll(incoming);
        validateRecipes();
    }

    private void validateRecipes() {
        for (CrossRecipe recipe : recipes) {
            CrossIngredient result = recipe.getResult();
            if (result.getType() == CrossIngredient.Type.MYTHICMOBS && mythic != null) {
                if (!mythic.itemExists(result.getId()))
                    plugin.getLogger().warning("Recipe '" + recipe.getName()
                            + "': result MM item '" + result.getId() + "' not found.");
            }
            if (result.getType() == CrossIngredient.Type.ITEMSADDER && ia != null) {
                if (!ia.itemExists(result.getId()))
                    plugin.getLogger().warning("Recipe '" + recipe.getName()
                            + "': result IA item '" + result.getId() + "' not found.");
            }
        }
    }

    public CrossRecipe findMatch(ItemStack[] matrix) {
        for (CrossRecipe recipe : recipes) {
            if (recipe.matches(matrix, mythic, ia)) return recipe;
        }
        return null;
    }

    public List<CrossRecipe> getRecipes() {
        return Collections.unmodifiableList(recipes);
    }
}
