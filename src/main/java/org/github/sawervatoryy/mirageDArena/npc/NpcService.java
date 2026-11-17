package org.github.sawervatoryy.mirageDArena.npc;

import org.github.sawervatoryy.mirageDArena.MirageDArena;
import org.github.sawervatoryy.mirageDArena.config.Settings;
import org.github.sawervatoryy.mirageDArena.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Нативные NPC арены (сущности без ИИ), без зависимости от Citizens.
 * NPC не сохраняются в мире (non-persistent) и пересоздаются при старте/перезагрузке;
 * чанки NPC удерживаются plugin chunk ticket, а раз в N секунд проверяется, что NPC на месте.
 */
public final class NpcService {

    private final MirageDArena plugin;
    private final NamespacedKey idKey;
    private final Map<String, UUID> tracked = new ConcurrentHashMap<>();
    private BukkitTask healthTask;

    public NpcService(MirageDArena plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "npc_id");
    }

    public int count() {
        return tracked.size();
    }

    /** Пересоздаёт всех NPC по текущему config.yml. */
    public void reload() {
        despawnAll();
        Settings.Npc cfg = plugin.settings().npc();
        if (!cfg.enabled()) {
            return;
        }
        cfg.list().values().forEach(this::spawn);
        long period = cfg.healthCheckSeconds() * 20L;
        healthTask = Bukkit.getScheduler().runTaskTimer(plugin, this::healthCheck, period, period);
    }

    public void despawnAll() {
        if (healthTask != null) {
            healthTask.cancel();
            healthTask = null;
        }
        for (UUID id : tracked.values()) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        tracked.clear();
        for (World w : Bukkit.getWorlds()) {
            w.removePluginChunkTickets(plugin);
        }
    }

    /** Спавн NPC, чей мир только что загрузился. */
    public void spawnForWorld(World world) {
        if (!plugin.settings().npc().enabled()) {
            return;
        }
        plugin.settings().npc().list().values().stream()
                .filter(def -> def.location().world().equals(world.getName()))
                .forEach(this::spawn);
    }

    /** Определение NPC по сущности или null, если это не (актуальный) NPC арены. */
    public Settings.NpcDef definitionOf(Entity entity) {
        String id = entity.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        if (id == null || !entity.getUniqueId().equals(tracked.get(id))) {
            return null;
        }
        return plugin.settings().npc().list().get(id);
    }

    public boolean isNpc(Entity entity) {
        return entity.getPersistentDataContainer().has(idKey, PersistentDataType.STRING);
    }

    private void spawn(Settings.NpcDef def) {
        Location loc = def.location().toLocation();
        if (loc == null) {
            plugin.debug("NPC " + def.id() + ": мир " + def.location().world() + " ещё не загружен");
            return;
        }
        // чанк грузится асинхронно; создание сущности — на главном потоке
        loc.getWorld().getChunkAtAsync(loc).thenAccept(chunk -> plugin.sync(() -> {
            chunk.addPluginChunkTicket(plugin);
            create(def, loc);
        }));
    }

    @SuppressWarnings("unchecked")
    private void create(Settings.NpcDef def, Location loc) {
        UUID existingId = tracked.get(def.id());
        Entity existing = existingId == null ? null : Bukkit.getEntity(existingId);
        if (existing != null && existing.isValid()) {
            return;
        }
        Class<? extends LivingEntity> type = (Class<? extends LivingEntity>) def.type().getEntityClass();
        LivingEntity entity = loc.getWorld().spawn(loc, type, e -> configure(e, def));
        tracked.put(def.id(), entity.getUniqueId());
    }

    private void configure(LivingEntity e, Settings.NpcDef def) {
        e.setAI(false);
        e.setInvulnerable(true);
        e.setSilent(true);
        e.setPersistent(false);
        e.setRemoveWhenFarAway(false);
        e.setCollidable(false);
        e.customName(Text.color(def.name()));
        e.setCustomNameVisible(true);
        e.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, def.id());
        if (e instanceof Mob mob) {
            mob.setAware(false);
        }
        if (e instanceof Villager villager && !def.profession().isBlank()) {
            Villager.Profession p = Registry.VILLAGER_PROFESSION.get(
                    NamespacedKey.minecraft(def.profession().toLowerCase(Locale.ROOT)));
            if (p != null) {
                villager.setProfession(p);
                villager.setVillagerExperience(1);
            }
        }
    }

    private void healthCheck() {
        for (Settings.NpcDef def : plugin.settings().npc().list().values()) {
            UUID id = tracked.get(def.id());
            Entity e = id == null ? null : Bukkit.getEntity(id);
            if (e == null || !e.isValid()) {
                spawn(def);
            }
        }
    }
}
