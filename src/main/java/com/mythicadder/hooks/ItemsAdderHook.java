package com.mythicadder.hooks;

import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.inventory.ItemStack;

public class ItemsAdderHook {

    public boolean isItemsAdderItem(ItemStack stack, String namespacedId) {
        if (stack == null) return false;
        CustomStack cs = CustomStack.byItemStack(stack);
        if (cs == null) return false;
        return cs.getNamespacedID().equalsIgnoreCase(namespacedId);
    }

    public String getItemsAdderName(ItemStack stack) {
        if (stack == null) return null;
        CustomStack cs = CustomStack.byItemStack(stack);
        return cs != null ? cs.getNamespacedID() : null;
    }

    public ItemStack getItemStack(String namespacedId, int amount) {
        try {
            CustomStack cs = CustomStack.getInstance(namespacedId);
            if (cs == null) return null;
            ItemStack stack = cs.getItemStack().clone();
            stack.setAmount(amount);
            return stack;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean itemExists(String namespacedId) {
        try {
            return CustomStack.getInstance(namespacedId) != null;
        } catch (Exception e) {
            return false;
        }
    }
}
