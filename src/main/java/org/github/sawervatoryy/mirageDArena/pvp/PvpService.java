package org.github.sawervatoryy.mirageDArena.pvp;

import org.github.sawervatoryy.mirageDArena.MirageDArena;
import org.github.sawervatoryy.mirageDArena.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Постоянное обновление PvP-режима игроков на арене.
 * Ошибки провайдеров логируются один раз на провайдера, чтобы не засорять консоль.
 */
public final class PvpService {

    private final MirageDArena plugin;
    private final Set<String> failed = ConcurrentHashMap.newKeySet();
    private volatile List<PvpProvider> providers = List.of();
    private BukkitTask task;

    public PvpService(MirageDArena plugin) {
        this.plugin = plugin;
    }

    /** Пересобирает провайдеры и перезапускает задачу обновления. */
    public void reload() {
        stop();
        failed.clear();
        Settings.Pvp cfg = plugin.settings().pvp();
        List<PvpProvider> list = new ArrayList<>();
        for (Settings.Provider p : cfg.providers()) {
            if (p.isEmpty()) {
                plugin.debug("PvP-провайдер '" + p.id() + "' пропущен: списки команд пусты");
                continue;
            }
            list.add(new CommandPvpProvider(plugin, p));
        }
        providers = List.copyOf(list);
        if (cfg.enabled()) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, cfg.refreshTicks(), cfg.refreshTicks());
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public int providerCount() {
        return providers.size();
    }

    public void onJoin(Player player) {
        forEachAvailable(p -> p.onJoin(player));
        keepWorldPvp(player.getWorld());
    }

    public void onLeave(Player player) {
        forEachAvailable(p -> p.onLeave(player));
    }

    private void tick() {
        for (UUID id : plugin.arena().members()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                continue;
            }
            keepWorldPvp(player.getWorld());
            forEachAvailable(p -> p.refresh(player));
        }
    }

    /** PvP в мире арены должен быть включён всегда. */
    private void keepWorldPvp(World world) {
        if (plugin.settings().pvp().forceWorldPvp() && !world.getPVP()
                && plugin.settings().arena().worlds().containsKey(world.getName())) {
            world.setPVP(true);
        }
    }

    private void forEachAvailable(java.util.function.Consumer<PvpProvider> action) {
        for (PvpProvider provider : providers) {
            if (!provider.available()) {
                continue;
            }
            try {
                action.accept(provider);
            } catch (RuntimeException e) {
                if (failed.add(provider.id())) {
                    plugin.getLogger().warning("PvP-провайдер '" + provider.id() + "' сообщил об ошибке: " + e);
                }
            }
        }
    }
}
