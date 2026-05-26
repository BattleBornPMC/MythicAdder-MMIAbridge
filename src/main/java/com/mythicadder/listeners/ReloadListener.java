package com.mythicadder.listeners;

import com.mythicadder.MythicAdder;
import dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent;
import io.lumine.mythic.bukkit.events.MythicReloadedEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class ReloadListener implements Listener {

    private final MythicAdder plugin;

    public ReloadListener(MythicAdder plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onItemsAdderLoad(ItemsAdderLoadDataEvent event) {
        if (event.getCause() == ItemsAdderLoadDataEvent.Cause.FIRST_LOAD
                || event.getCause() == ItemsAdderLoadDataEvent.Cause.RELOAD) {
            plugin.getLogger().info("ItemsAdder (re)loaded — rescanning IA recipes...");
            plugin.rescan();
        }
    }

    @EventHandler
    public void onMythicReload(MythicReloadedEvent event) {
        plugin.getLogger().info("MythicMobs reloaded — rescanning MM recipes...");
        plugin.rescan();
    }
}
