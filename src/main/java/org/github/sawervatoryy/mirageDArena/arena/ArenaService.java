package org.github.sawervatoryy.mirageDArena.arena;

import org.github.sawervatoryy.mirageDArena.MirageDArena;
import org.github.sawervatoryy.mirageDArena.config.Point;
import org.github.sawervatoryy.mirageDArena.config.Settings;
import org.github.sawervatoryy.mirageDArena.event.ArenaJoinEvent;
import org.github.sawervatoryy.mirageDArena.event.ArenaLeaveEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Состояние арены: кто на ней находится, вход и выход.
 * Флаг «на арене», место возврата и выбранный мир хранятся в PersistentDataContainer игрока,
 * поэтому переживают перезагрузку сервера и перезаход.
 */
public final class ArenaService {

    private final MirageDArena plugin;
    private final Set<UUID> members = ConcurrentHashMap.newKeySet();
    private final Set<UUID> joining = ConcurrentHashMap.newKeySet();
    private final NamespacedKey flagKey;
    private final NamespacedKey returnKey;
    private final NamespacedKey selectedKey;

    public ArenaService(MirageDArena plugin) {
        this.plugin = plugin;
        this.flagKey = new NamespacedKey(plugin, "in_arena");
        this.returnKey = new NamespacedKey(plugin, "return_point");
        this.selectedKey = new NamespacedKey(plugin, "selected_world");
    }

    public boolean isMember(UUID id) {
        return members.contains(id);
    }

    public Set<UUID> members() {
        return Collections.unmodifiableSet(members);
    }

    public boolean isArenaWorld(World world) {
        return world != null && plugin.settings().arena().worlds().containsKey(world.getName());
    }

    /** Ключ мира, выбранного игроком (или мир по умолчанию). */
    public String selectedWorld(Player player) {
        String stored = player.getPersistentDataContainer().get(selectedKey, PersistentDataType.STRING);
        Settings.Arena arena = plugin.settings().arena();
        return stored != null && arena.worlds().containsKey(stored) ? stored : arena.defaultWorld();
    }

    public void selectWorld(Player player, String key) {
        player.getPersistentDataContainer().set(selectedKey, PersistentDataType.STRING, key);
    }

    /** Вход на арену. requestedKey == null — использовать выбранный/стандартный мир. */
    public void join(Player player, String requestedKey) {
        UUID id = player.getUniqueId();
        if (members.contains(id) || joining.contains(id)) {
            plugin.messages().send(player, "arena.already-in");
            return;
        }
        Settings.Arena cfg = plugin.settings().arena();
        String key = requestedKey != null ? requestedKey : selectedWorld(player);
        Settings.ArenaWorld target = cfg.worlds().get(key);
        World world = target == null ? null : Bukkit.getWorld(target.key());
        if (world == null) {
            plugin.messages().send(player, "arena.world-unavailable", "world", String.valueOf(key));
            return;
        }
        ArenaJoinEvent event = new ArenaJoinEvent(player, target.key());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            plugin.messages().send(player, "arena.join-cancelled");
            return;
        }
        Location from = player.getLocation().clone();
        Location destination = target.spawn() != null ? target.spawn().toLocation() : null;
        if (destination == null) {
            destination = world.getSpawnLocation();
        }
        joining.add(id);
        player.teleportAsync(destination).whenComplete((ok, error) -> plugin.sync(() -> {
            joining.remove(id);
            if (error != null || !Boolean.TRUE.equals(ok) || !player.isOnline()) {
                plugin.messages().send(player, "arena.teleport-failed");
                return;
            }
            register(player, target, from);
        }));
    }

    private void register(Player player, Settings.ArenaWorld target, Location from) {
        members.add(player.getUniqueId());
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(flagKey, PersistentDataType.BYTE, (byte) 1);
        if (plugin.settings().arena().rememberReturn() && !isArenaWorld(from.getWorld())) {
            pdc.set(returnKey, PersistentDataType.STRING, Point.of(from).encode());
        }
        Settings.Join join = plugin.settings().join();
        if (join.gameMode() != null) {
            player.setGameMode(join.gameMode());
        }
        if (join.clearEffects()) {
            player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
        }
        plugin.pvp().onJoin(player);
        plugin.messages().send(player, "arena.joined", "world", target.displayName(), "world_key", target.key());
    }

    /** Завершает выход: снимает PvP-режим и телепортирует игрока в точку выхода. */
    public void leave(Player player, LeaveCause cause) {
        UUID id = player.getUniqueId();
        if (!members.remove(id)) {
            return;
        }
        plugin.leave().cancelSilently(id);
        plugin.pvp().onLeave(player);
        Location destination = resolveExit(player);
        clearState(player);
        Bukkit.getPluginManager().callEvent(new ArenaLeaveEvent(player, cause));
        player.teleportAsync(destination).whenComplete((ok, error) -> plugin.sync(() -> {
            if (player.isOnline()) {
                plugin.messages().send(player, "arena.left");
            }
        }));
    }

    /** Убирает игрока с арены без телепортации (смерть без респавна на арене, смена мира). */
    public void detach(Player player) {
        if (!members.remove(player.getUniqueId())) {
            return;
        }
        plugin.leave().cancelSilently(player.getUniqueId());
        plugin.pvp().onLeave(player);
        clearState(player);
        Bukkit.getPluginManager().callEvent(new ArenaLeaveEvent(player, LeaveCause.FORCED));
        plugin.messages().send(player, "arena.forced-out");
    }

    /** Восстанавливает участие игрока после перезахода/рестарта. */
    public void restore(Player player) {
        if (members.add(player.getUniqueId())) {
            plugin.pvp().onJoin(player);
            plugin.messages().send(player, "arena.restored");
        }
    }

    /** Убирает игрока из набора при выходе с сервера; флаг в PDC остаётся для restore(). */
    public void forget(UUID id) {
        members.remove(id);
        joining.remove(id);
    }

    public boolean hasFlag(Player player) {
        return player.getPersistentDataContainer().has(flagKey, PersistentDataType.BYTE);
    }

    public void clearFlag(Player player) {
        player.getPersistentDataContainer().remove(flagKey);
    }

    /** Точка возрождения при отказе от респавна на арене / точка выхода. */
    public Location resolveExit(Player player) {
        Settings.Arena cfg = plugin.settings().arena();
        Location result = null;
        if (cfg.rememberReturn()) {
            Point saved = Point.decode(player.getPersistentDataContainer().get(returnKey, PersistentDataType.STRING));
            result = saved == null ? null : saved.toLocation();
            if (result != null && isArenaWorld(result.getWorld())) {
                result = null;
            }
        }
        if (result == null && cfg.exit() != null) {
            result = cfg.exit().toLocation();
        }
        if (result == null) {
            for (World w : Bukkit.getWorlds()) {
                if (!isArenaWorld(w)) {
                    result = w.getSpawnLocation();
                    break;
                }
            }
        }
        return result != null ? result : Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    /** Точка входа на арену для текущего мира игрока (респавн). */
    public Location arenaSpawn(Player player) {
        Settings.ArenaWorld aw = plugin.settings().arena().worlds().get(selectedWorld(player));
        if (aw == null) {
            return null;
        }
        Location loc = aw.spawn() != null ? aw.spawn().toLocation() : null;
        if (loc == null) {
            World w = Bukkit.getWorld(aw.key());
            loc = w == null ? null : w.getSpawnLocation();
        }
        return loc;
    }

    private void clearState(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.remove(flagKey);
        pdc.remove(returnKey);
    }
}
