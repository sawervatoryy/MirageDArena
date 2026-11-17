package org.github.sawervatoryy.mirageDArena.event;

import org.github.sawervatoryy.mirageDArena.arena.LeaveCause;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/** Вызывается, когда игрок покинул арену (после снятия PvP-режима). */
public class ArenaLeaveEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final LeaveCause cause;

    public ArenaLeaveEvent(Player player, LeaveCause cause) {
        super(player);
        this.cause = cause;
    }

    public LeaveCause getCause() {
        return cause;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
