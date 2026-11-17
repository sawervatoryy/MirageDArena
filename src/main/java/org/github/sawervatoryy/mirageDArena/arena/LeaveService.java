package org.github.sawervatoryy.mirageDArena.arena;

import org.github.sawervatoryy.mirageDArena.MirageDArena;
import org.github.sawervatoryy.mirageDArena.config.Settings;
import org.github.sawervatoryy.mirageDArena.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Отложенный выход: 30 сек по /leave, 15 сек через NPC (настраивается). */
public final class LeaveService {

    private final MirageDArena plugin;
    private final Map<UUID, Countdown> active = new ConcurrentHashMap<>();

    public LeaveService(MirageDArena plugin) {
        this.plugin = plugin;
    }

    public boolean isLeaving(UUID id) {
        return active.containsKey(id);
    }

    /** Запускает отсчёт. Повторный вызов может только сократить оставшееся время. */
    public void start(Player player, LeaveCause cause) {
        UUID id = player.getUniqueId();
        if (!plugin.arena().isMember(id)) {
            plugin.messages().send(player, "arena.not-in");
            return;
        }
        Settings.Leave cfg = plugin.settings().leave();
        int delay = cause == LeaveCause.NPC ? cfg.npcDelay() : cfg.commandDelay();
        if (delay <= 0) {
            plugin.arena().leave(player, cause);
            return;
        }
        Countdown current = active.get(id);
        if (current != null) {
            if (delay < current.remaining) {
                current.remaining = delay;
                current.cause = cause;
                plugin.messages().send(player, "leave.shortened", "seconds", String.valueOf(delay));
                display(player, delay);
            } else {
                plugin.messages().send(player, "leave.already-leaving", "seconds", String.valueOf(current.remaining));
            }
            return;
        }
        Countdown countdown = new Countdown(delay, player.getLocation().clone(), cause);
        active.put(id, countdown);
        plugin.messages().send(player, cause == LeaveCause.NPC ? "leave.started-npc" : "leave.started",
                "seconds", String.valueOf(delay));
        display(player, delay);
        countdown.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(id), 20L, 20L);
    }

    private void tick(UUID id) {
        Countdown c = active.get(id);
        Player player = Bukkit.getPlayer(id);
        if (c == null || player == null || !plugin.arena().isMember(id)) {
            cancelSilently(id);
            return;
        }
        c.remaining--;
        if (c.remaining <= 0) {
            LeaveCause cause = c.cause;
            cancelSilently(id);
            plugin.arena().leave(player, cause);
            return;
        }
        display(player, c.remaining);
    }

    private void display(Player player, int seconds) {
        Settings.Leave cfg = plugin.settings().leave();
        String s = String.valueOf(seconds);
        if (cfg.actionBar()) {
            String raw = plugin.messages().raw("leave.actionbar", "seconds", s);
            if (!raw.isBlank()) {
                player.sendActionBar(Text.color(raw));
            }
        }
        if (cfg.title()) {
            Component title = plugin.messages().get("leave.title", "seconds", s);
            Component subtitle = plugin.messages().get("leave.subtitle", "seconds", s);
            player.showTitle(Title.title(title, subtitle,
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO)));
        }
    }

    /** Отмена игроком/по условию — с сообщением и очисткой титула. messageKey может быть null. */
    public boolean cancel(Player player, String messageKey) {
        if (!cancelSilently(player.getUniqueId())) {
            return false;
        }
        player.clearTitle();
        player.sendActionBar(Component.empty());
        if (messageKey != null) {
            plugin.messages().send(player, messageKey);
        }
        return true;
    }

    public boolean cancelSilently(UUID id) {
        Countdown c = active.remove(id);
        if (c == null) {
            return false;
        }
        if (c.task != null) {
            c.task.cancel();
        }
        return true;
    }

    public void cancelAll() {
        active.keySet().forEach(this::cancelSilently);
    }

    /** Проверка движения (вызывается из слушателя; быстрый выход, если отсчёта нет). */
    public void checkMove(Player player, Location to) {
        Countdown c = active.get(player.getUniqueId());
        if (c == null || !plugin.settings().leave().cancelOnMove()) {
            return;
        }
        double tol = plugin.settings().leave().moveTolerance();
        if (!to.getWorld().equals(c.origin.getWorld()) || c.origin.distanceSquared(to) > tol * tol) {
            cancel(player, "leave.cancelled-move");
        }
    }

    public void checkDamage(Player player) {
        if (plugin.settings().leave().cancelOnDamage()) {
            cancel(player, "leave.cancelled-damage");
        }
    }

    private static final class Countdown {
        private int remaining;
        private final Location origin;
        private LeaveCause cause;
        private BukkitTask task;

        private Countdown(int remaining, Location origin, LeaveCause cause) {
            this.remaining = remaining;
            this.origin = origin;
            this.cause = cause;
        }
    }
}
