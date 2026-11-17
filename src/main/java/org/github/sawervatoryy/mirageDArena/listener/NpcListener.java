package org.github.sawervatoryy.mirageDArena.listener;

import org.github.sawervatoryy.mirageDArena.MirageDArena;
import org.github.sawervatoryy.mirageDArena.arena.LeaveCause;
import org.github.sawervatoryy.mirageDArena.config.Settings;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Взаимодействие с NPC арены. Выход через NPC работает и в PvP-режиме. */
public final class NpcListener implements Listener {

    private static final long CLICK_COOLDOWN_MS = 500;

    private final MirageDArena plugin;
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

    public NpcListener(MirageDArena plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (!plugin.npcs().isNpc(clicked)) {
            return;
        }
        // отменяем всегда (в т.ч. торговлю у жителя), даже если другой плагин уже отменил
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Settings.NpcDef def = plugin.npcs().definitionOf(clicked);
        Player player = event.getPlayer();
        if (def == null || !player.hasPermission("miragedarena.npc.use")) {
            return;
        }
        long now = System.currentTimeMillis();
        Long prev = lastClick.put(player.getUniqueId(), now);
        if (prev != null && now - prev < CLICK_COOLDOWN_MS) {
            return;
        }
        switch (def.role()) {
            case JOIN -> plugin.arena().join(player, null);
            case LEAVE -> handleLeave(player);
        }
    }

    private void handleLeave(Player player) {
        if (!plugin.arena().isMember(player.getUniqueId())) {
            plugin.messages().send(player, "arena.not-in");
            return;
        }
        if (plugin.settings().leave().npcWorldSelect() && plugin.settings().arena().worlds().size() > 1) {
            plugin.worldMenu().open(player);
        } else {
            plugin.leave().start(player, LeaveCause.NPC);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (plugin.npcs().isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCombust(EntityCombustEvent event) {
        if (plugin.npcs().isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        plugin.npcs().spawnForWorld(event.getWorld());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastClick.remove(event.getPlayer().getUniqueId());
    }
}
