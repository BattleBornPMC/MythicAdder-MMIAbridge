package com.mythicadder.recipe;

import com.mythicadder.hooks.ItemsAdderHook;
import com.mythicadder.hooks.MythicHook;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class CrossRecipe {

    public enum RecipeType { SHAPED, SHAPELESS }

    private final String name;
    private final RecipeType recipeType;
    private final CrossIngredient result;
    private final int resultAmount;

    // Shaped
    private final String[] shape;
    private final Map<Character, CrossIngredient> ingredientMap;

    // Shapeless
    private final List<CrossIngredient> ingredients;

    // Precomputed: how many non-empty items this recipe expects in the grid.
    // Used as a cheap pre-filter before any MM/IA API calls.
    private final int requiredItemCount;

    public CrossRecipe(String name, CrossIngredient result, int resultAmount,
                       String[] shape, Map<Character, CrossIngredient> ingredientMap) {
        this.name = name;
        this.recipeType = RecipeType.SHAPED;
        this.result = result;
        this.resultAmount = resultAmount;
        this.shape = shape;
        this.ingredientMap = ingredientMap;
        this.ingredients = null;
        this.requiredItemCount = computeShapedCount(shape, ingredientMap);
    }

    public CrossRecipe(String name, CrossIngredient result, int resultAmount,
                       List<CrossIngredient> ingredients) {
        this.name = name;
        this.recipeType = RecipeType.SHAPELESS;
        this.result = result;
        this.resultAmount = resultAmount;
        this.shape = null;
        this.ingredientMap = null;
        this.ingredients = ingredients;
        this.requiredItemCount = ingredients.size();
    }

    private static int computeShapedCount(String[] shape, Map<Character, CrossIngredient> map) {
        int count = 0;
        for (String row : shape) {
            for (char c : row.toCharArray()) {
                CrossIngredient ing = map.get(c);
                if (ing != null && !ing.isEmpty()) count++;
            }
        }
        return count;
    }

    private static int nonEmptyCount(ItemStack[] matrix) {
        int count = 0;
        for (ItemStack s : matrix) {
            if (s != null && s.getType() != Material.AIR) count++;
        }
        return count;
    }

    public boolean matches(ItemStack[] matrix, MythicHook mythic, ItemsAdderHook ia) {
        // Cheap count check before any MM/IA API calls
        if (nonEmptyCount(matrix) != requiredItemCount) return false;
        return recipeType == RecipeType.SHAPED
                ? matchesShaped(matrix, mythic, ia)
                : matchesShapeless(matrix, mythic, ia);
    }

    private boolean matchesShaped(ItemStack[] matrix, MythicHook mythic, ItemsAdderHook ia) {
        if (matrix.length < 9) return false; // 2x2 inventory grid — not supported for shaped recipes
        int rows = shape.length;
        int cols = Arrays.stream(shape).mapToInt(String::length).max().orElse(0);
        if (rows > 3 || cols > 3) return false;

        for (int rowOff = 0; rowOff <= 3 - rows; rowOff++) {
            for (int colOff = 0; colOff <= 3 - cols; colOff++) {
                if (matchesShapedAt(matrix, rowOff, colOff, mythic, ia)) return true;
            }
        }
        return false;
    }

    private boolean matchesShapedAt(ItemStack[] matrix, int rowOff, int colOff,
                                    MythicHook mythic, ItemsAdderHook ia) {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                ItemStack stack = matrix[r * 3 + c];
                CrossIngredient expected = ingredientAt(r, c, rowOff, colOff);
                if (!expected.matches(stack, mythic, ia)) return false;
            }
        }
        return true;
    }

    private CrossIngredient ingredientAt(int r, int c, int rowOff, int colOff) {
        int recipeRow = r - rowOff;
        int recipeCol = c - colOff;

        if (recipeRow < 0 || recipeRow >= shape.length || recipeCol < 0) {
            return CrossIngredient.EMPTY;
        }
        String row = shape[recipeRow];
        if (recipeCol >= row.length()) return CrossIngredient.EMPTY;

        char key = row.charAt(recipeCol);
        if (key == ' ') return CrossIngredient.EMPTY;

        return ingredientMap.getOrDefault(key, CrossIngredient.EMPTY);
    }

    private boolean matchesShapeless(ItemStack[] matrix, MythicHook mythic, ItemsAdderHook ia) {
        List<ItemStack> nonEmpty = new ArrayList<>();
        for (ItemStack stack : matrix) {
            if (stack != null && stack.getType() != Material.AIR) nonEmpty.add(stack);
        }
        if (nonEmpty.size() != ingredients.size()) return false;

        List<CrossIngredient> remaining = new ArrayList<>(ingredients);
        for (ItemStack stack : nonEmpty) {
            boolean found = false;
            for (int i = 0; i < remaining.size(); i++) {
                if (remaining.get(i).matches(stack, mythic, ia)) {
                    remaining.remove(i);
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return remaining.isEmpty();
    }

    public ItemStack buildResult(MythicHook mythic, ItemsAdderHook ia) {
        return switch (result.getType()) {
            case MYTHICMOBS -> mythic != null ? mythic.getItemStack(result.getId(), resultAmount) : null;
            case ITEMSADDER -> ia != null ? ia.getItemStack(result.getId(), resultAmount) : null;
            case VANILLA -> {
                Material mat = Material.matchMaterial(result.getId().toUpperCase());
                yield mat != null ? new ItemStack(mat, resultAmount) : null;
            }
        };
    }

    /** Returns a human-readable description of why this recipe did not match the given matrix. */
    public String debugFailReason(ItemStack[] matrix, MythicHook mythic, ItemsAdderHook ia) {
        if (recipeType == RecipeType.SHAPELESS) {
            long nonEmpty = java.util.Arrays.stream(matrix)
                    .filter(s -> s != null && s.getType() != Material.AIR).count();
            if (nonEmpty != ingredients.size())
                return "shapeless: grid has " + nonEmpty + " item(s), recipe needs " + ingredients.size();
            return "shapeless: ingredient type/id mismatch";
        }

        int rows = shape.length;
        int cols = Arrays.stream(shape).mapToInt(String::length).max().orElse(0);
        StringBuilder sb = new StringBuilder("shaped: no offset matched. shape=")
                .append(Arrays.toString(shape))
                .append(" ingredients=");
        ingredientMap.forEach((k, v) -> sb.append(k).append("->").append(v).append(" "));
        sb.append("| grid slots with items: ");
        for (int i = 0; i < matrix.length; i++) {
            ItemStack s = matrix[i];
            if (s != null && s.getType() != Material.AIR) {
                String mmName = mythic != null ? mythic.getMythicName(s).orElse(null) : null;
                String iaName = ia != null ? ia.getItemsAdderName(s) : null;
                sb.append("slot").append(i).append("=");
                if (mmName != null) sb.append("MM:").append(mmName);
                else if (iaName != null) sb.append("IA:").append(iaName);
                else sb.append(s.getType());
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    public String getName() { return name; }
    public RecipeType getRecipeType() { return recipeType; }
    public CrossIngredient getResult() { return result; }
    public int getResultAmount() { return resultAmount; }
    public String[] getShape() { return shape; }
    public Map<Character, CrossIngredient> getIngredientMap() { return ingredientMap; }
    public List<CrossIngredient> getIngredients() { return ingredients; }
}
