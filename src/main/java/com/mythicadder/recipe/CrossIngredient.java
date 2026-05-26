package com.mythicadder.recipe;

import com.mythicadder.hooks.ItemsAdderHook;
import com.mythicadder.hooks.MythicHook;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class CrossIngredient {

    public enum Type { VANILLA, MYTHICMOBS, ITEMSADDER }

    public static final CrossIngredient EMPTY = new CrossIngredient(Type.VANILLA, "AIR");

    private final Type type;
    private final String id;

    public CrossIngredient(Type type, String id) {
        this.type = type;
        this.id = id;
    }

    public Type getType() { return type; }
    public String getId() { return id; }

    public boolean isEmpty() {
        return type == Type.VANILLA && (id == null || id.isBlank()
                || id.equalsIgnoreCase("AIR") || id.equalsIgnoreCase("EMPTY"));
    }

    public boolean matches(ItemStack stack, MythicHook mythic, ItemsAdderHook ia) {
        if (isEmpty()) {
            return stack == null || stack.getType() == Material.AIR;
        }
        if (stack == null || stack.getType() == Material.AIR) return false;

        return switch (type) {
            case VANILLA -> {
                Material mat = Material.matchMaterial(id.toUpperCase());
                yield mat != null && stack.getType() == mat;
            }
            case MYTHICMOBS -> mythic != null && mythic.isMythicItem(stack, id);
            case ITEMSADDER -> ia != null && ia.isItemsAdderItem(stack, id);
        };
    }

    @Override
    public String toString() {
        return type.name() + ":" + id;
    }
}
