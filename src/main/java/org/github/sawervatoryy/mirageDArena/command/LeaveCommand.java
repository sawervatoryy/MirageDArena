package org.github.sawervatoryy.mirageDArena.command;

import org.github.sawervatoryy.mirageDArena.MirageDArena;
import org.github.sawervatoryy.mirageDArena.arena.LeaveCause;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

/** /leave — выход с арены с задержкой; /leave cancel — отмена. */
public final class LeaveCommand implements TabExecutor {

    private final MirageDArena plugin;

    public LeaveCommand(MirageDArena plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.players-only");
            return true;
        }
        if (!player.hasPermission("miragedarena.leave")) {
            plugin.messages().send(player, "general.no-permission");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("cancel")) {
            if (!plugin.leave().cancel(player, "leave.cancelled")) {
                plugin.messages().send(player, "leave.nothing-to-cancel");
            }
            return true;
        }
        plugin.leave().start(player, LeaveCause.COMMAND);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 && "cancel".startsWith(args[0].toLowerCase()) ? List.of("cancel") : List.of();
    }
}
