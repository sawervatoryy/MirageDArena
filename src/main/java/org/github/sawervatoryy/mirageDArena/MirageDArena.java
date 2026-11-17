package org.github.sawervatoryy.mirageDArena;

import org.github.sawervatoryy.mirageDArena.arena.ArenaService;
import org.github.sawervatoryy.mirageDArena.arena.LeaveService;
import org.github.sawervatoryy.mirageDArena.command.ArenaCommand;
import org.github.sawervatoryy.mirageDArena.command.LeaveCommand;
import org.github.sawervatoryy.mirageDArena.config.ConfigService;
import org.github.sawervatoryy.mirageDArena.config.Settings;
import org.github.sawervatoryy.mirageDArena.gui.WorldSelectMenu;
import org.github.sawervatoryy.mirageDArena.lang.Messages;
import org.github.sawervatoryy.mirageDArena.listener.ArenaListener;
import org.github.sawervatoryy.mirageDArena.listener.NpcListener;
import org.github.sawervatoryy.mirageDArena.npc.NpcService;
import org.github.sawervatoryy.mirageDArena.pvp.PvpService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.Executor;

/**
 * MirageDArena — арена смерти для Paper 1.21.4.
 * Класс только собирает сервисы и слушатели; логика живёт в отдельных классах.
 */
public final class MirageDArena extends JavaPlugin {

    private Messages messages;
    private ConfigService configService;
    private PvpService pvpService;
    private ArenaService arenaService;
    private LeaveService leaveService;
    private NpcService npcService;
    private WorldSelectMenu worldSelectMenu;

    @Override
    public void onEnable() {
        messages = new Messages(getLogger());
        configService = new ConfigService(this, messages);
        try {
            configService.loadBlocking();
        } catch (Exception e) {
            getLogger().severe("Не удалось загрузить конфигурацию: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        pvpService = new PvpService(this);
        arenaService = new ArenaService(this);
        leaveService = new LeaveService(this);
        npcService = new NpcService(this);
        worldSelectMenu = new WorldSelectMenu(this);

        Bukkit.getPluginManager().registerEvents(new ArenaListener(this), this);
        Bukkit.getPluginManager().registerEvents(new NpcListener(this), this);
        Bukkit.getPluginManager().registerEvents(worldSelectMenu, this);

        bind("leave", new LeaveCommand(this));
        ArenaCommand arenaCommand = new ArenaCommand(this);
        bind("darena", arenaCommand);

        onSettingsApplied();
        getLogger().info("MirageDArena " + getPluginMeta().getVersion() + " включён.");
    }

    @Override
    public void onDisable() {
        if (pvpService != null) {
            pvpService.stop();
        }
        if (leaveService != null) {
            leaveService.cancelAll();
        }
        if (npcService != null) {
            npcService.despawnAll();
        }
        if (configService != null) {
            configService.shutdown();
        }
    }

    /** Вызывается после загрузки/изменения конфигурации: пересобирает зависящие от неё сервисы. */
    public void onSettingsApplied() {
        pvpService.reload();
        npcService.reload();
        if (settings().arena().forceWorldPvp()) {
            settings().arena().worlds().keySet().forEach(key -> {
                World w = Bukkit.getWorld(key);
                if (w != null) {
                    w.setPVP(true);
                }
            });
        }
    }

    private void bind(String name, org.bukkit.command.TabExecutor executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().severe("Команда " + name + " не найдена в plugin.yml");
            return;
        }
        cmd.setExecutor(executor);
        cmd.setTabCompleter(executor);
    }

    /** Выполняет задачу на главном потоке (сразу, если уже на нём). */
    public void sync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else if (isEnabled()) {
            Bukkit.getScheduler().runTask(this, task);
        }
    }

    public Executor mainExecutor() {
        return this::sync;
    }

    /** Отладочный вывод: только при debug: true. */
    public void debug(String message) {
        if (settings().debug()) {
            getLogger().info("[debug] " + message);
        }
    }

    public Settings settings() {
        return configService.settings();
    }

    public Messages messages() {
        return messages;
    }

    public ConfigService configService() {
        return configService;
    }

    public PvpService pvp() {
        return pvpService;
    }

    public ArenaService arena() {
        return arenaService;
    }

    public LeaveService leave() {
        return leaveService;
    }

    public NpcService npcs() {
        return npcService;
    }

    public WorldSelectMenu worldMenu() {
        return worldSelectMenu;
    }
}
