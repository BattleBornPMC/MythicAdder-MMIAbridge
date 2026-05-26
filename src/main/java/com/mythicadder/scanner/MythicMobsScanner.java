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
 * Scans MythicMobs' Items YAMLs for crafting recipes that contain
 * itemsadder:<namespace>:<id> ingredients and converts them into CrossRecipes.
 *
 * In your MythicMobs item YAML, use this format for an ingredient:
 *
 *   Recipes:
 *     Crafting:
 *       Type: SHAPED
 *       Shape:
 *         - "ABA"
 *       Ingredients:
 *         A: itemsadder:mypack:ruby 1
 *         B: STICK 1
 *
 * MythicMobs will warn about the unknown ingredient; MMIAbridge intercepts
 * the crafting event and handles the recipe instead.
 */
public class MythicMobsScanner {

    private static final String IA_PREFIX = "itemsadder:";

    private final Plugin plugin;
    private final Logger log;

    public MythicMobsScanner(Plugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    public List<CrossRecipe> scan(File mmDataFolder) {
        List<CrossRecipe> found = new ArrayList<>();
        if (mmDataFolder == null || !mmDataFolder.exists()) return found;

        File itemsDir = new File(mmDataFolder, "Items");
        if (!itemsDir.exists()) return found;

        walkDirectory(itemsDir, found);
        log.info("[MM Scanner] Found " + found.size() + " recipe(s) with ItemsAdder ingredients.");
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
        try {
            if (!Files.readString(file.toPath()).contains(IA_PREFIX)) return;
        } catch (IOException e) {
            return;
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        for (String itemKey : cfg.getKeys(false)) {
            ConfigurationSection item = cfg.getConfigurationSection(itemKey);
            if (item == null) continue;

            ConfigurationSection recipesSec = item.getConfigurationSection("Recipes");
            if (recipesSec == null) continue;

            // MythicMobs groups recipes under "Crafting" (there can also be Furnace, etc.)
            Object craftingRaw = recipesSec.get("Crafting");

            if (craftingRaw instanceof List<?> craftingList) {
                // Multiple crafting recipes for one item
                for (int i = 0; i < craftingList.size(); i++) {
                    if (craftingList.get(i) instanceof Map<?, ?> map) {
                        parseCraftingEntry(itemKey + "_" + i, itemKey, mapToSection(map), found);
                    }
                }
            } else {
                ConfigurationSection craftSec = recipesSec.getConfigurationSection("Crafting");
                if (craftSec != null) {
                    parseCraftingEntry(itemKey, itemKey, craftSec, found);
                }
            }
        }
    }

    private void parseCraftingEntry(String recipeName, String mythicItemKey,
                                    ConfigurationSection craft, List<CrossRecipe> found) {
        String type = craft.getString("Type", "SHAPED").toUpperCase();
        int amount = craft.getInt("Amount", 1);

        CrossIngredient result = new CrossIngredient(CrossIngredient.Type.MYTHICMOBS, mythicItemKey);

        CrossRecipe recipe = switch (type) {
            case "SHAPED" -> parseShaped(craft, recipeName, result, amount);
            case "SHAPELESS" -> parseShapeless(craft, recipeName, result, amount);
            default -> null;
        };

        if (recipe != null) found.add(recipe);
    }

    private CrossRecipe parseShaped(ConfigurationSection craft, String recipeName,
                                    CrossIngredient result, int amount) {
        List<String> shape = craft.getStringList("Shape");
        if (shape.isEmpty()) return null;

        ConfigurationSection ingSection = craft.getConfigurationSection("Ingredients");
        if (ingSection == null) return null;

        Map<Character, CrossIngredient> ingredientMap = new HashMap<>();
        boolean hasIA = false;

        for (String key : ingSection.getKeys(false)) {
            if (key.length() != 1) continue;
            char c = key.charAt(0);
            String raw = ingSection.getString(key, "");
            CrossIngredient ing = parseIngredient(raw);
            ingredientMap.put(c, ing);
            if (ing.getType() == CrossIngredient.Type.ITEMSADDER) hasIA = true;
        }

        if (!hasIA) return null;
        return new CrossRecipe(recipeName, result, amount, shape.toArray(new String[0]), ingredientMap);
    }

    private CrossRecipe parseShapeless(ConfigurationSection craft, String recipeName,
                                       CrossIngredient result, int amount) {
        // MythicMobs shapeless can be a list under Ingredients
        List<?> rawList = craft.getList("Ingredients");
        if (rawList == null || rawList.isEmpty()) return null;

        List<CrossIngredient> ingredients = new ArrayList<>();
        boolean hasIA = false;

        for (Object raw : rawList) {
            CrossIngredient ing = parseIngredient(raw.toString());
            ingredients.add(ing);
            if (ing.getType() == CrossIngredient.Type.ITEMSADDER) hasIA = true;
        }

        if (!hasIA || ingredients.isEmpty()) return null;
        return new CrossRecipe(recipeName, result, amount, ingredients);
    }

    /**
     * Parses a MythicMobs ingredient string into a CrossIngredient.
     *
     * Format: "INGREDIENT_ID [AMOUNT]"
     *
     * itemsadder:mypack:ruby [1] -> ITEMSADDER (id = "mypack:ruby")
     * DIAMOND [1]                -> VANILLA
     * MySword [1]                -> MYTHICMOBS (if not a valid Material)
     */
    private CrossIngredient parseIngredient(String raw) {
        if (raw == null || raw.isBlank()) return CrossIngredient.EMPTY;

        // Strip trailing amount (e.g. "DIAMOND 1" -> "DIAMOND")
        String id = stripAmount(raw.trim());

        if (id.startsWith(IA_PREFIX)) {
            // itemsadder:namespace:item -> CrossIngredient id is "namespace:item"
            return new CrossIngredient(CrossIngredient.Type.ITEMSADDER, id.substring(IA_PREFIX.length()));
        }
        if (Material.matchMaterial(id.toUpperCase()) != null) {
            return new CrossIngredient(CrossIngredient.Type.VANILLA, id.toUpperCase());
        }
        // Assume it's a MythicMobs item name
        return new CrossIngredient(CrossIngredient.Type.MYTHICMOBS, id);
    }

    private String stripAmount(String raw) {
        int lastSpace = raw.lastIndexOf(' ');
        if (lastSpace < 0) return raw;
        String possibleAmount = raw.substring(lastSpace + 1);
        try {
            Integer.parseInt(possibleAmount);
            return raw.substring(0, lastSpace);
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    @SuppressWarnings("unchecked")
    private ConfigurationSection mapToSection(Map<?, ?> map) {
        // Convert a raw Map back into a ConfigurationSection-like wrapper via a temp YamlConfig
        YamlConfiguration tmp = new YamlConfiguration();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            tmp.set(entry.getKey().toString(), entry.getValue());
        }
        return tmp;
    }
}
