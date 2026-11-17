package org.github.sawervatoryy.mirageDArena.command;

import org.github.sawervatoryy.mirageDArena.MirageDArena;
import org.github.sawervatoryy.mirageDArena.arena.LeaveCause;
import org.github.sawervatoryy.mirageDArena.config.Point;
import org.github.sawervatoryy.mirageDArena.config.Settings;
import org.github.sawervatoryy.mirageDArena.npc.NpcRole;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** /darena — игровые и административные подкоманды. */
public final class ArenaCommand implements TabExecutor {

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final String ADMIN = "miragedarena.admin";

    private final MirageDArena plugin;

    public ArenaCommand(MirageDArena plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> plugin.messages().sendList(sender, "help");
            case "join" -> join(sender, args);
            case "reload" -> admin(sender, () -> reload(sender));
            case "status" -> admin(sender, () -> plugin.messages().send(sender, "admin.status",
                    "players", String.valueOf(plugin.arena().members().size()),
                    "npcs", String.valueOf(plugin.npcs().count()),
                    "providers", String.valueOf(plugin.pvp().providerCount())));
            case "kick" -> admin(sender, () -> kick(sender, args));
            case "setexit" -> admin(sender, () -> setExit(sender));
            case "world" -> admin(sender, () -> world(sender, args));
            case "npc" -> admin(sender, () -> npc(sender, args));
            default -> plugin.messages().send(sender, "general.unknown-subcommand");
        }
        return true;
    }

    private void admin(CommandSender sender, Runnable action) {
        if (!sender.hasPermission(ADMIN)) {
            plugin.messages().send(sender, "general.no-permission");
            return;
        }
        action.run();
    }

    private Player playerOrNull(CommandSender sender) {
        if (sender instanceof Player p) {
            return p;
        }
        plugin.messages().send(sender, "general.players-only");
        return null;
    }

    private void join(CommandSender sender, String[] args) {
        Player player = playerOrNull(sender);
        if (player == null) {
            return;
        }
        if (!player.hasPermission("miragedarena.join")) {
            plugin.messages().send(player, "general.no-permission");
        } else if (!plugin.settings().join().allowCommand() && !player.hasPermission(ADMIN)) {
            plugin.messages().send(player, "arena.join-disabled");
        } else {
            plugin.arena().join(player, args.length > 1 ? args[1] : null);
        }
    }

    private void reload(CommandSender sender) {
        long start = System.currentTimeMillis();
        plugin.configService().reload().whenComplete((v, error) -> plugin.sync(() -> {
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                plugin.messages().send(sender, "admin.reload-failed", "error", String.valueOf(cause.getMessage()));
                return;
            }
            plugin.onSettingsApplied();
            plugin.messages().send(sender, "admin.reloaded", "ms", String.valueOf(System.currentTimeMillis() - start));
        }));
    }

    private void kick(CommandSender sender, String[] args) {
        Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
        if (target == null || !plugin.arena().isMember(target.getUniqueId())) {
            plugin.messages().send(sender, "admin.player-not-found");
            return;
        }
        plugin.arena().leave(target, LeaveCause.FORCED);
        plugin.messages().send(sender, "admin.forced-leave", "player", target.getName());
    }

    private void setExit(CommandSender sender) {
        Player player = playerOrNull(sender);
        if (player == null) {
            return;
        }
        Point point = Point.of(player.getLocation());
        plugin.configService().edit(cfg -> point.write(cfg.createSection("arena.exit")));
        plugin.messages().send(player, "admin.exit-set");
    }

    private void world(CommandSender sender, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        Settings.Arena arena = plugin.settings().arena();
        switch (action) {
            case "list" -> {
                plugin.messages().send(sender, "admin.world-list-header", "count", String.valueOf(arena.worlds().size()));
                arena.worlds().values().forEach(w -> plugin.messages().send(sender, "admin.world-list-entry",
                        "key", w.key(), "display", w.displayName()));
            }
            case "add" -> {
                String name = args.length > 2 ? args[2] : null;
                World world = name == null ? null : Bukkit.getWorld(name);
                if (world == null || name.contains(".")) {
                    plugin.messages().send(sender, "admin.world-not-found", "world", String.valueOf(name));
                } else if (arena.worlds().containsKey(name)) {
                    plugin.messages().send(sender, "admin.world-exists", "world", name);
                } else {
                    plugin.configService().edit(cfg -> {
                        cfg.set("arena.worlds." + name + ".display-name", "&#00ACFF«&f " + name + " &#00ACFF»");
                        cfg.set("arena.worlds." + name + ".icon", "DIAMOND_SWORD");
                        if (arena.worlds().isEmpty()) {
                            cfg.set("arena.default-world", name);
                        }
                    });
                    plugin.messages().send(sender, "admin.world-added", "world", name);
                }
            }
            case "remove" -> {
                String name = args.length > 2 ? args[2] : null;
                if (name == null || !arena.worlds().containsKey(name)) {
                    plugin.messages().send(sender, "admin.world-not-found", "world", String.valueOf(name));
                } else {
                    plugin.configService().edit(cfg -> cfg.set("arena.worlds." + name, null));
                    plugin.messages().send(sender, "admin.world-removed", "world", name);
                }
            }
            case "setspawn" -> {
                Player player = playerOrNull(sender);
                if (player == null) {
                    return;
                }
                String name = args.length > 2 ? args[2] : player.getWorld().getName();
                if (!arena.worlds().containsKey(name)) {
                    plugin.messages().send(player, "admin.world-not-found", "world", name);
                    return;
                }
                Point point = Point.of(player.getLocation());
                plugin.configService().edit(cfg -> point.write(cfg.createSection("arena.worlds." + name + ".spawn")));
                plugin.messages().send(player, "admin.spawn-set", "world", name);
            }
            default -> plugin.messages().send(sender, "general.unknown-subcommand");
        }
    }

    private void npc(CommandSender sender, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        Settings.Npc npcs = plugin.settings().npc();
        if (!npcs.enabled()) {
            plugin.messages().send(sender, "npc.disabled");
            return;
        }
        switch (action) {
            case "list" -> {
                if (npcs.list().isEmpty()) {
                    plugin.messages().send(sender, "npc.list-empty");
                    return;
                }
                plugin.messages().send(sender, "npc.list-header", "count", String.valueOf(npcs.list().size()));
                npcs.list().values().forEach(d -> plugin.messages().send(sender, "npc.list-entry",
                        "id", d.id(), "role", d.role().name(), "world", d.location().world(),
                        "x", String.valueOf(Math.round(d.location().x())),
                        "y", String.valueOf(Math.round(d.location().y())),
                        "z", String.valueOf(Math.round(d.location().z()))));
            }
            case "create" -> {
                Player player = playerOrNull(sender);
                if (player == null) {
                    return;
                }
                String id = args.length > 2 ? args[2] : "";
                if (!ID.matcher(id).matches()) {
                    plugin.messages().send(player, "npc.invalid-id");
                    return;
                }
                if (npcs.list().containsKey(id)) {
                    plugin.messages().send(player, "npc.exists", "id", id);
                    return;
                }
                NpcRole role;
                try {
                    role = NpcRole.valueOf((args.length > 3 ? args[3] : "").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    plugin.messages().send(player, "npc.invalid-role");
                    return;
                }
                Point point = Point.of(player.getLocation());
                final String roleName = role.name();
                plugin.configService().edit(cfg -> {
                    cfg.set("npc.list." + id + ".role", roleName);
                    point.write(cfg.createSection("npc.list." + id + ".location"));
                });
                plugin.messages().send(player, "npc.created", "id", id, "role", roleName);
            }
            case "remove" -> {
                String id = args.length > 2 ? args[2] : "";
                if (!npcs.list().containsKey(id)) {
                    plugin.messages().send(sender, "npc.not-found", "id", id);
                    return;
                }
                plugin.configService().edit(cfg -> cfg.set("npc.list." + id, null));
                plugin.messages().send(sender, "npc.removed", "id", id);
            }
            default -> plugin.messages().send(sender, "general.unknown-subcommand");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission(ADMIN);
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("help", "join"));
            if (admin) {
                out.addAll(List.of("reload", "status", "kick", "setexit", "world", "npc"));
            }
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "join" -> out.addAll(plugin.settings().arena().worlds().keySet());
                case "kick" -> plugin.arena().members().forEach(id -> {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && admin) {
                        out.add(p.getName());
                    }
                });
                case "world" -> {
                    if (admin) {
                        out.addAll(List.of("add", "remove", "setspawn", "list"));
                    }
                }
                case "npc" -> {
                    if (admin) {
                        out.addAll(List.of("create", "remove", "list"));
                    }
                }
                default -> {
                }
            }
        } else if (args.length == 3 && admin) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String act = args[1].toLowerCase(Locale.ROOT);
            if (sub.equals("world") && act.equals("add")) {
                Bukkit.getWorlds().forEach(w -> out.add(w.getName()));
            } else if (sub.equals("world")) {
                out.addAll(plugin.settings().arena().worlds().keySet());
            } else if (sub.equals("npc") && act.equals("remove")) {
                out.addAll(plugin.settings().npc().list().keySet());
            }
        } else if (args.length == 4 && admin && args[0].equalsIgnoreCase("npc") && args[1].equalsIgnoreCase("create")) {
            out.addAll(List.of("join", "leave"));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
