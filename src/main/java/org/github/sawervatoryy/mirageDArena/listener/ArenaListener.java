package org.github.sawervatoryy.mirageDArena.listener;

import org.github.sawervatoryy.mirageDArena.MirageDArena;
import org.github.sawervatoryy.mirageDArena.config.Settings;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Locale;

/** События арены: команды в PvP-режиме, смерть/респавн, перезаход, отмена выхода. */
public final class ArenaListener implements Listener {

    private final MirageDArena plugin;

    public ArenaListener(MirageDArena plugin) {
        this.plugin = plugin;
    }

    /**
     * HIGHEST: выполняется после большинства PvP-плагинов, поэтому может снять их отмену
     * для команд из белого списка (/leave должен работать в PvP-режиме).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!plugin.arena().isMember(player.getUniqueId())) {
            return;
        }
        Settings.Commands cfg = plugin.settings().commands();
        String message = event.getMessage();
        int space = message.indexOf(' ');
        String label = (space < 0 ? message.substring(1) : message.substring(1, space)).toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        if (cfg.allowed().contains(label)) {
            if (cfg.forceAllow() && event.isCancelled()) {
                event.setCancelled(false);
            }
            return;
        }
        if (cfg.restrict() && !player.hasPermission("miragedarena.bypass.commands")) {
            event.setCancelled(true);
            plugin.messages().send(player, "commands.blocked");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!plugin.arena().hasFlag(player)) {
            return;
        }
        if (plugin.arena().isArenaWorld(player.getWorld())) {
            plugin.arena().restore(player);
        } else {
            plugin.arena().clearFlag(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.leave().cancelSilently(event.getPlayer().getUniqueId());
        plugin.arena().forget(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (plugin.arena().isMember(player.getUniqueId()) && !plugin.arena().isArenaWorld(player.getWorld())) {
            plugin.arena().detach(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (plugin.arena().isMember(player.getUniqueId()) && plugin.settings().arena().keepInventory()) {
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!plugin.arena().isMember(player.getUniqueId())) {
            return;
        }
        plugin.leave().cancelSilently(player.getUniqueId());
        if (plugin.settings().arena().respawnInArena()) {
            Location spawn = plugin.arena().arenaSpawn(player);
            if (spawn != null) {
                event.setRespawnLocation(spawn);
                return;
            }
        }
        event.setRespawnLocation(plugin.arena().resolveExit(player));
        plugin.arena().detach(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.hasChangedPosition()) {
            plugin.leave().checkMove(event.getPlayer(), event.getTo());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && plugin.leave().isLeaving(player.getUniqueId())) {
            plugin.leave().checkDamage(player);
        }
    }
}
