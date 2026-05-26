package com.mythicadder.scanner;

import com.mythicadder.recipe.CrossIngredient;
import com.mythicadder.recipe.CrossRecipe;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Scans ItemsAdder's config YAMLs for crafting recipes that contain
 * mythicmobs:<ItemName> ingredients and converts them into CrossRecipes.
 *
 * In your ItemsAdder item YAML, use this format for an ingredient:
 *
 *   ingredients:
 *     A:
 *       item: mythicmobs:MyMythicItem
 *
 * ItemsAdder will ignore/warn about the unknown ingredient; MMIAbridge
 * intercepts the crafting event and handles the recipe instead.
 */
public class ItemsAdderScanner {

    private static final String MYTHIC_PREFIX = "mythicmobs:";

    private final Plugin plugin;
    private final Logger log;

    public ItemsAdderScanner(Plugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    public List<CrossRecipe> scan(File iaDataFolder) {
        List<CrossRecipe> found = new ArrayList<>();
        if (iaDataFolder == null || !iaDataFolder.exists()) return found;

        walkDirectory(iaDataFolder, found);
        log.info("[IA Scanner] Found " + found.size() + " recipe(s) with MythicMobs ingredients.");
        return found;
    }

    private void walkDirectory(File dir, List<CrossRecipe> found) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) walkDirectory(f, found);
            else if (f.getName().endsWith(".yml")) parseFile(f, found);
        }
    }

    private void parseFile(File file, List<CrossRecipe> found) {
        // Quick pre-check to avoid full YAML parse on unrelated files
        try {
            if (!Files.readString(file.toPath()).contains(MYTHIC_PREFIX)) return;
        } catch (IOException e) {
            return;
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        String namespace = cfg.getString("info.namespace");

        // Items with inline recipes
        ConfigurationSection items = cfg.getConfigurationSection("items");
        if (items != null) {
            for (String itemId : items.getKeys(false)) {
                ConfigurationSection item = items.getConfigurationSection(itemId);
                if (item == null) continue;
                parseItemRecipes(item, namespace, itemId, file.getName(), found);
            }
        }

        // Standalone recipe sections (some IA setups use a top-level "recipes:" block)
        ConfigurationSection recipes = cfg.getConfigurationSection("recipes");
        if (recipes != null) {
            parseStandaloneRecipes(recipes, namespace, file.getName(), found);
        }
    }

    private void parseItemRecipes(ConfigurationSection item, String namespace, String itemId,
                                  String fileName, List<CrossRecipe> found) {
        ConfigurationSection recipeSec = item.getConfigurationSection("recipe");
        if (recipeSec == null) return;

        ConfigurationSection craftSec = recipeSec.getConfigurationSection("crafting_table");
        if (craftSec == null) return;

        // Inline item recipes default the result to the item itself
        String defaultResult = (namespace != null ? namespace + ":" : "") + itemId;
        String recipeName = fileName.replace(".yml", "") + "_" + itemId;
        parseCraftSection(craftSec, recipeName, defaultResult, found);
    }

    private void parseStandaloneRecipes(ConfigurationSection recipes, String namespace,
                                        String fileName, List<CrossRecipe> found) {
        ConfigurationSection craftSec = recipes.getConfigurationSection("crafting_table");
        if (craftSec == null) return;

        for (String recipeId : craftSec.getKeys(false)) {
            ConfigurationSection r = craftSec.getConfigurationSection(recipeId);
            if (r == null) continue;
            String recipeName = (namespace != null ? namespace + "_" : "") + recipeId;
            parseCraftSection(r, recipeName, "", found);
        }
    }

    // result can be "result: ns:item" (string) or "result:\n  item: ns:item\n  amount: 1" (section)
    private void parseCraftSection(ConfigurationSection craft, String recipeName,
                                   String defaultResultId, List<CrossRecipe> found) {
        String type = craft.getString("recipe_type", "shaped").toLowerCase();

        String resultId;
        int resultAmount;
        ConfigurationSection resultSec = craft.getConfigurationSection("result");
        if (resultSec != null) {
            resultId = resultSec.getString("item", defaultResultId);
            resultAmount = resultSec.getInt("amount", 1);
        } else {
            resultId = craft.getString("result", defaultResultId);
            resultAmount = craft.getInt("amount", 1);
        }

        CrossIngredient result = new CrossIngredient(CrossIngredient.Type.ITEMSADDER, resultId);

        CrossRecipe recipe = switch (type) {
            case "shaped" -> parseShaped(craft, recipeName, result, resultAmount);
            case "shapeless" -> parseShapeless(craft, recipeName, result, resultAmount);
            default -> null;
        };

        if (recipe != null) found.add(recipe);
    }

    private CrossRecipe parseShaped(ConfigurationSection craft, String recipeName,
                                    CrossIngredient result, int amount) {
        List<String> pattern = craft.getStringList("pattern");
        if (pattern.isEmpty()) return null;

        ConfigurationSection ingMap = craft.getConfigurationSection("ingredients");
        if (ingMap == null) return null;

        Map<Character, CrossIngredient> ingredientMap = new HashMap<>();
        boolean hasMythic = false;

        for (String key : ingMap.getKeys(false)) {
            if (key.length() != 1) continue;
            char c = key.charAt(0);

            String itemId;
            ConfigurationSection ingSec = ingMap.getConfigurationSection(key);
            if (ingSec != null) {
                // Nested format:  O:\n  item: mythicmobs:ale
                itemId = ingSec.getString("item", "");
            } else {
                // Direct format:  O: mythicmobs:ale
                itemId = ingMap.getString(key, "");
            }

            CrossIngredient ing = parseIngredient(itemId);
            ingredientMap.put(c, ing);
            if (ing.getType() == CrossIngredient.Type.MYTHICMOBS) hasMythic = true;
        }

        if (!hasMythic) return null;
        return new CrossRecipe(recipeName, result, amount, pattern.toArray(new String[0]), ingredientMap);
    }

    private CrossRecipe parseShapeless(ConfigurationSection craft, String recipeName,
                                       CrossIngredient result, int amount) {
        List<?> rawList = craft.getList("ingredients");
        if (rawList == null || rawList.isEmpty()) return null;

        List<CrossIngredient> ingredients = new ArrayList<>();
        boolean hasMythic = false;

        for (Object raw : rawList) {
            String itemId = null;
            if (raw instanceof Map<?, ?> map) {
                Object v = map.get("item");
                if (v != null) itemId = v.toString();
            } else if (raw instanceof String s) {
                itemId = s;
            }
            if (itemId == null) continue;
            CrossIngredient ing = parseIngredient(itemId);
            ingredients.add(ing);
            if (ing.getType() == CrossIngredient.Type.MYTHICMOBS) hasMythic = true;
        }

        if (!hasMythic || ingredients.isEmpty()) return null;
        return new CrossRecipe(recipeName, result, amount, ingredients);
    }

    /**
     * Parses an ItemsAdder ingredient string into a CrossIngredient.
     *
     * mythicmobs:ItemName  -> MYTHICMOBS
     * namespace:item_id    -> ITEMSADDER
     * DIAMOND              -> VANILLA
     */
    private CrossIngredient parseIngredient(String raw) {
        if (raw == null || raw.isBlank()) return CrossIngredient.EMPTY;

        if (raw.startsWith(MYTHIC_PREFIX)) {
            return new CrossIngredient(CrossIngredient.Type.MYTHICMOBS, raw.substring(MYTHIC_PREFIX.length()));
        }
        // ItemsAdder items use namespace:id — they contain a colon but no "mythicmobs:" prefix
        if (raw.contains(":")) {
            return new CrossIngredient(CrossIngredient.Type.ITEMSADDER, raw);
        }
        return new CrossIngredient(CrossIngredient.Type.VANILLA, raw.toUpperCase());
    }
}
