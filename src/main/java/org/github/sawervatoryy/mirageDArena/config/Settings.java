package org.github.sawervatoryy.mirageDArena.config;

import org.github.sawervatoryy.mirageDArena.npc.NpcRole;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Неизменяемый снимок config.yml. Создаётся при (пере)загрузке и подменяется целиком,
 * поэтому безопасен для чтения из любых потоков.
 */
public record Settings(
        boolean debug,
        Arena arena,
        Join join,
        Leave leave,
        Pvp pvp,
        Commands commands,
        Npc npc) {

    public record ArenaWorld(String key, String displayName, Material icon, Point spawn) {
    }

    public record Arena(String defaultWorld, boolean forceWorldPvp, boolean respawnInArena, boolean keepInventory,
                        boolean rememberReturn, Point exit, Map<String, ArenaWorld> worlds) {
    }

    public record Join(boolean allowCommand, GameMode gameMode, boolean clearEffects) {
    }

    public record Leave(int commandDelay, int npcDelay, boolean cancelOnMove, double moveTolerance,
                        boolean cancelOnDamage, boolean actionBar, boolean title, boolean npcWorldSelect) {
    }

    public record Provider(String id, String plugin, List<String> start, List<String> refresh, List<String> end) {
        public boolean isEmpty() {
            return start.isEmpty() && refresh.isEmpty() && end.isEmpty();
        }
    }

    public record Pvp(boolean enabled, int refreshTicks, boolean forceWorldPvp, List<Provider> providers) {
    }

    public record Commands(boolean restrict, Set<String> allowed, boolean forceAllow) {
    }

    public record NpcDef(String id, NpcRole role, Point location, EntityType type, String name, String profession) {
    }

    public record Npc(boolean enabled, int healthCheckSeconds, Map<String, NpcDef> list) {
    }

    /** Разбирает конфиг. Ошибки в отдельных записях логируются и не ломают остальное. */
    public static Settings from(FileConfiguration c, Logger log) {
        boolean forcePvp = c.getBoolean("arena.force-world-pvp", true);

        // --- arena ---
        Map<String, ArenaWorld> worlds = new LinkedHashMap<>();
        ConfigurationSection ws = c.getConfigurationSection("arena.worlds");
        if (ws != null) {
            for (String key : ws.getKeys(false)) {
                ConfigurationSection s = ws.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                Material icon = Material.matchMaterial(s.getString("icon", "DIAMOND_SWORD"));
                worlds.put(key, new ArenaWorld(
                        key,
                        s.getString("display-name", "&#00ACFF«&f " + key + " &#00ACFF»"),
                        icon == null || icon.isAir() ? Material.DIAMOND_SWORD : icon,
                        Point.read(s.getConfigurationSection("spawn"), key)));
            }
        }
        if (worlds.isEmpty()) {
            log.warning("arena.worlds пуст — добавьте мир: /darena world add <мир>");
        }
        String defaultWorld = c.getString("arena.default-world", "");
        if (!worlds.containsKey(defaultWorld) && !worlds.isEmpty()) {
            defaultWorld = worlds.keySet().iterator().next();
        }
        Arena arena = new Arena(defaultWorld, forcePvp,
                c.getBoolean("arena.respawn-in-arena", true),
                c.getBoolean("arena.keep-inventory", false),
                c.getBoolean("arena.remember-return-location", true),
                Point.read(c.getConfigurationSection("arena.exit"), null),
                Collections.unmodifiableMap(worlds));

        // --- join / leave ---
        String gm = c.getString("join.game-mode", "SURVIVAL");
        Join join = new Join(c.getBoolean("join.allow-command", true),
                "NONE".equalsIgnoreCase(gm) ? null : enumOf(GameMode.class, gm, GameMode.SURVIVAL, log),
                c.getBoolean("join.clear-effects", false));

        Leave leave = new Leave(
                Math.max(0, c.getInt("leave.command-delay-seconds", 30)),
                Math.max(0, c.getInt("leave.npc-delay-seconds", 15)),
                c.getBoolean("leave.cancel-on-move", true),
                Math.max(0.1, c.getDouble("leave.move-tolerance", 1.5)),
                c.getBoolean("leave.cancel-on-damage", false),
                c.getBoolean("leave.show-action-bar", true),
                c.getBoolean("leave.show-title", true),
                c.getBoolean("leave.npc-world-select", true));

        // --- pvp ---
        List<Provider> providers = new ArrayList<>();
        ConfigurationSection ps = c.getConfigurationSection("pvp.providers");
        if (ps != null) {
            for (String id : ps.getKeys(false)) {
                ConfigurationSection s = ps.getConfigurationSection(id);
                if (s == null || !s.getBoolean("enabled", true)) {
                    continue;
                }
                providers.add(new Provider(id, s.getString("plugin", ""),
                        List.copyOf(s.getStringList("start-commands")),
                        List.copyOf(s.getStringList("refresh-commands")),
                        List.copyOf(s.getStringList("end-commands"))));
            }
        }
        Pvp pvp = new Pvp(c.getBoolean("pvp.enabled", true),
                Math.max(1, c.getInt("pvp.refresh-interval-ticks", 20)),
                forcePvp, List.copyOf(providers));

        // --- commands ---
        Set<String> allowed = c.getStringList("commands.allowed").stream()
                .map(s -> s.startsWith("/") ? s.substring(1) : s)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        Commands commands = new Commands(c.getBoolean("commands.restrict-in-arena", true), allowed,
                c.getBoolean("commands.force-allow", true));

        // --- npc ---
        EntityType defType = entityOf(c.getString("npc.default-type", "VILLAGER"), EntityType.VILLAGER, log);
        String joinName = c.getString("npc.names.join", "&#00ACFF⌈&f Вход на арену &#00ACFF⌋");
        String leaveName = c.getString("npc.names.leave", "&#00ACFF⌈&f Выход с арены &#00ACFF⌋");
        Map<String, NpcDef> npcs = new LinkedHashMap<>();
        ConfigurationSection ns = c.getConfigurationSection("npc.list");
        if (ns != null) {
            for (String id : ns.getKeys(false)) {
                ConfigurationSection s = ns.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                NpcRole role = enumOf(NpcRole.class, s.getString("role", ""), null, log);
                Point where = Point.read(s.getConfigurationSection("location"), null);
                if (role == null || where == null) {
                    log.warning("NPC '" + id + "' пропущен: нужны корректные role и location");
                    continue;
                }
                EntityType type = s.isString("type") ? entityOf(s.getString("type"), defType, log) : defType;
                String name = s.getString("name", role == NpcRole.JOIN ? joinName : leaveName);
                npcs.put(id, new NpcDef(id, role, where, type, name, s.getString("profession", "")));
            }
        }
        Npc npc = new Npc(c.getBoolean("npc.enabled", true),
                Math.max(1, c.getInt("npc.health-check-seconds", 10)),
                Collections.unmodifiableMap(npcs));

        return new Settings(c.getBoolean("debug", false), arena, join, leave, pvp, commands, npc);
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String value, E fallback, Logger log) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning("Неизвестное значение '" + value + "' для " + type.getSimpleName());
            return fallback;
        }
    }

    private static EntityType entityOf(String value, EntityType fallback, Logger log) {
        EntityType type = enumOf(EntityType.class, value, fallback, log);
        if (type == null || !type.isSpawnable() || type.getEntityClass() == null
                || !LivingEntity.class.isAssignableFrom(type.getEntityClass())) {
            log.warning("Тип сущности '" + value + "' не подходит для NPC, используется " + fallback);
            return fallback;
        }
        return type;
    }
}
