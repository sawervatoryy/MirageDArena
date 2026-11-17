package org.github.sawervatoryy.mirageDArena.config;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/** Неизменяемая точка в мире (мир хранится по имени, чтобы не держать ссылку на World). */
public record Point(String world, double x, double y, double z, float yaw, float pitch) {

    public static Point of(Location l) {
        return new Point(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    /** Читает секцию; defaultWorld используется, если поле world не задано. */
    public static Point read(ConfigurationSection s, String defaultWorld) {
        if (s == null) {
            return null;
        }
        String world = s.getString("world", defaultWorld);
        if (world == null || world.isBlank()) {
            return null;
        }
        return new Point(world, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    public void write(ConfigurationSection s) {
        s.set("world", world);
        s.set("x", x);
        s.set("y", y);
        s.set("z", z);
        s.set("yaw", (double) yaw);
        s.set("pitch", (double) pitch);
    }

    /** Локация или null, если мир не загружен. */
    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }

    /** Компактная запись для PersistentDataContainer. */
    public String encode() {
        return world + ";" + x + ";" + y + ";" + z + ";" + yaw + ";" + pitch;
    }

    public static Point decode(String raw) {
        if (raw == null) {
            return null;
        }
        String[] p = raw.split(";");
        if (p.length != 6) {
            return null;
        }
        try {
            return new Point(p[0], Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                    Float.parseFloat(p[4]), Float.parseFloat(p[5]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
