package org.github.sawervatoryy.mirageDArena.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/** Вызывается перед входом игрока на арену. Можно отменить. */
public class ArenaJoinEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final String worldKey;
    private boolean cancelled;

    public ArenaJoinEvent(Player player, String worldKey) {
        super(player);
        this.worldKey = worldKey;
    }

    public String getWorldKey() {
        return worldKey;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
