package org.github.sawervatoryy.mirageDArena.util;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Set;

/**
 * «Тихая» консоль: ведёт себя как ConsoleCommandSender (права, instanceof),
 * но проглатывает любые сообщения. Нужна, чтобы периодические команды
 * PvP-плагинов не засоряли консоль ответами.
 */
public final class SilentConsole {

    private static final Set<String> MUTED = Set.of(
            "sendMessage", "sendRichMessage", "sendPlainMessage", "sendActionBar", "sendRawMessage");

    private static volatile CommandSender instance;

    private SilentConsole() {
    }

    public static CommandSender get() {
        CommandSender local = instance;
        if (local == null) {
            synchronized (SilentConsole.class) {
                if (instance == null) {
                    instance = create();
                }
                local = instance;
            }
        }
        return local;
    }

    private static CommandSender create() {
        ConsoleCommandSender real = Bukkit.getConsoleSender();
        return (CommandSender) Proxy.newProxyInstance(
                SilentConsole.class.getClassLoader(),
                new Class<?>[]{ConsoleCommandSender.class},
                (proxy, method, args) -> {
                    if (MUTED.contains(method.getName())) {
                        return null;
                    }
                    try {
                        return method.invoke(real, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }
}
