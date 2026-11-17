package org.github.sawervatoryy.mirageDArena.gui;

import org.github.sawervatoryy.mirageDArena.MirageDArena;
import org.github.sawervatoryy.mirageDArena.arena.LeaveCause;
import org.github.sawervatoryy.mirageDArena.config.Settings;
import org.github.sawervatoryy.mirageDArena.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Меню выбора мира арены смерти (открывается при выходе через NPC). */
public final class WorldSelectMenu implements Listener {

    private final MirageDArena plugin;

    public WorldSelectMenu(MirageDArena plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Map<String, Settings.ArenaWorld> worlds = plugin.settings().arena().worlds();
        int rows = Math.min(6, Math.max(1, (worlds.size() + 8) / 9));
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, rows * 9, plugin.messages().get("menu.title"));
        holder.inventory = inv;

        String current = plugin.arena().selectedWorld(player);
        int slot = 0;
        for (Settings.ArenaWorld world : worlds.values()) {
            if (slot >= rows * 9) {
                break;
            }
            ItemStack item = new ItemStack(world.icon());
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Text.color(world.displayName()).decoration(TextDecoration.ITALIC, false));
            String marker = world.key().equals(current) ? plugin.messages().raw("menu.current-marker") : "";
            List<Component> lore = new ArrayList<>();
            for (String line : plugin.messages().rawList("menu.world-lore", "marker", marker)) {
                if (!line.isBlank()) {
                    lore.add(Text.color(line).decoration(TextDecoration.ITALIC, false));
                }
            }
            meta.lore(lore);
            item.setItemMeta(meta);
            inv.setItem(slot, item);
            holder.slots.put(slot, world);
            slot++;
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getInventory()) {
            return;
        }
        Settings.ArenaWorld chosen = holder.slots.get(event.getSlot());
        if (chosen == null) {
            return;
        }
        player.closeInventory();
        plugin.arena().selectWorld(player, chosen.key());
        plugin.messages().send(player, "menu.selected", "world", chosen.displayName(), "world_key", chosen.key());
        plugin.leave().start(player, LeaveCause.NPC);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private static final class Holder implements InventoryHolder {
        private final Map<Integer, Settings.ArenaWorld> slots = new HashMap<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
