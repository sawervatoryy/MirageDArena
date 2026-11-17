package org.github.sawervatoryy.mirageDArena.lang;

import org.github.sawervatoryy.mirageDArena.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/** Доступ к lang.yml. Недостающие ключи логируются ровно один раз. */
public final class Messages {

    private final Logger log;
    private final Set<String> reportedMissing = ConcurrentHashMap.newKeySet();
    private volatile FileConfiguration lang;

    public Messages(Logger log) {
        this.log = log;
    }

    public void load(FileConfiguration lang) {
        this.lang = lang;
        reportedMissing.clear();
    }

    /** Сырая строка с применёнными плейсхолдерами (без парсинга цветов). */
    public String raw(String key, String... pairs) {
        FileConfiguration l = lang;
        String s = l == null ? null : l.getString(key);
        if (s == null) {
            if (reportedMissing.add(key)) {
                log.warning("В lang.yml нет ключа: " + key);
            }
            return "&c" + key;
        }
        return Text.replace(s.replace("{prefix}", l.getString("prefix", "")), pairs);
    }

    public List<String> rawList(String key, String... pairs) {
        FileConfiguration l = lang;
        if (l == null || !l.isList(key)) {
            if (reportedMissing.add(key)) {
                log.warning("В lang.yml нет списка: " + key);
            }
            return List.of("&c" + key);
        }
        String prefix = l.getString("prefix", "");
        return l.getStringList(key).stream()
                .map(s -> Text.replace(s.replace("{prefix}", prefix), pairs))
                .toList();
    }

    public Component get(String key, String... pairs) {
        return Text.color(raw(key, pairs));
    }

    /** Отправляет сообщение; пустая строка в lang.yml = сообщение отключено. */
    public void send(CommandSender to, String key, String... pairs) {
        String raw = raw(key, pairs);
        if (!raw.isBlank()) {
            to.sendMessage(Text.color(raw));
        }
    }

    public void sendList(CommandSender to, String key, String... pairs) {
        rawList(key, pairs).forEach(line -> to.sendMessage(Text.color(line)));
    }
}
