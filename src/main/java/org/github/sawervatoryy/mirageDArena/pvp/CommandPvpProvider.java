package org.github.sawervatoryy.mirageDArena.pvp;

import org.github.sawervatoryy.mirageDArena.MirageDArena;
import org.github.sawervatoryy.mirageDArena.config.Settings;
import org.github.sawervatoryy.mirageDArena.util.SilentConsole;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

/** Универсальный провайдер: выполняет настроенные команды «тихо» от имени консоли. */
public final class CommandPvpProvider implements PvpProvider {

    private final MirageDArena plugin;
    private final Settings.Provider cfg;

    public CommandPvpProvider(MirageDArena plugin, Settings.Provider cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
    }

    @Override
    public String id() {
        return cfg.id();
    }

    @Override
    public boolean available() {
        return cfg.plugin().isBlank() || Bukkit.getPluginManager().isPluginEnabled(cfg.plugin());
    }

    @Override
    public void onJoin(Player player) {
        run(cfg.start(), player);
    }

    @Override
    public void refresh(Player player) {
        run(cfg.refresh(), player);
    }

    @Override
    public void onLeave(Player player) {
        run(cfg.end(), player);
    }

    private void run(List<String> commands, Player player) {
        for (String template : commands) {
            String cmd = template
                    .replace("{player}", player.getName())
                    .replace("{uuid}", player.getUniqueId().toString())
                    .replace("{world}", player.getWorld().getName());
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            Bukkit.dispatchCommand(SilentConsole.get(), cmd);
        }
    }
}
