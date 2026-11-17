package org.github.sawervatoryy.mirageDArena.pvp;

import org.bukkit.entity.Player;

/** Адаптер к PvP-плагину (боевой режим, тег и т.п.). Все методы вызываются на главном потоке. */
public interface PvpProvider {

    String id();

    /** Доступен ли провайдер сейчас (например, установлен ли плагин). */
    boolean available();

    void onJoin(Player player);

    void refresh(Player player);

    void onLeave(Player player);
}
