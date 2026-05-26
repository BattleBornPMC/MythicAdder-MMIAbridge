package com.mythicadder.hooks;

import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public class MythicHook {

    public boolean isMythicItem(ItemStack stack, String name) {
        if (stack == null) return false;
        return getMythicName(stack).map(n -> n.equalsIgnoreCase(name)).orElse(false);
    }

    public Optional<String> getMythicName(ItemStack stack) {
        if (stack == null) return Optional.empty();
        try {
            String name = MythicBukkit.inst().getItemManager().getMythicTypeFromItem(stack);
            return Optional.ofNullable(name);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public ItemStack getItemStack(String name, int amount) {
        try {
            ItemStack stack = MythicBukkit.inst().getItemManager().getItemStack(name);
            if (stack == null) return null;
            stack = stack.clone();
            stack.setAmount(amount);
            return stack;
        } catch (Exception e) {
            return null;
        }
    }
}
