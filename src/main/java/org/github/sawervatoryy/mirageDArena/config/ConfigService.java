package org.github.sawervatoryy.mirageDArena.config;

import org.github.sawervatoryy.mirageDArena.MirageDArena;
import org.github.sawervatoryy.mirageDArena.lang.Messages;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Загрузка, перезагрузка и сохранение config.yml / lang.yml.
 * Чтение при перезагрузке и запись на диск выполняются на отдельном виртуальном потоке,
 * применение результата — на главном потоке.
 */
public final class ConfigService {

    private final MirageDArena plugin;
    private final Messages messages;
    private final ExecutorService io = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("MirageDArena-IO").factory());

    private volatile FileConfiguration config;
    private volatile Settings settings;

    public ConfigService(MirageDArena plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public Settings settings() {
        return settings;
    }

    /** Синхронная загрузка при старте сервера (единственное блокирующее чтение, один раз). */
    public void loadBlocking() throws IOException, InvalidConfigurationException {
        ensureFile("config.yml");
        ensureFile("lang.yml");
        apply(read());
    }

    /** Асинхронная перезагрузка; при ошибке прежняя конфигурация остаётся. */
    public CompletableFuture<Void> reload() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return read();
            } catch (IOException | InvalidConfigurationException e) {
                throw new CompletionException(e);
            }
        }, io).thenAcceptAsync(this::apply, plugin.mainExecutor());
    }

    /** Меняет конфиг на главном потоке, пересобирает Settings и сохраняет файл асинхронно. */
    public void edit(Consumer<FileConfiguration> change) {
        change.accept(config);
        settings = Settings.from(config, plugin.getLogger());
        saveAsync();
        plugin.onSettingsApplied();
    }

    public void saveAsync() {
        // сериализация — на главном потоке (конфиг не потокобезопасен), запись — в фоне
        final String data = config.saveToString();
        final File file = new File(plugin.getDataFolder(), "config.yml");
        io.execute(() -> {
            try {
                Files.writeString(file.toPath(), data, StandardCharsets.UTF_8);
            } catch (IOException e) {
                plugin.getLogger().severe("Не удалось сохранить config.yml: " + e.getMessage());
            }
        });
    }

    public void shutdown() {
        io.shutdown();
        try {
            if (!io.awaitTermination(5, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Фоновая запись не завершилась за 5 секунд");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void apply(Loaded loaded) {
        this.config = loaded.config();
        this.settings = Settings.from(loaded.config(), plugin.getLogger());
        this.messages.load(loaded.lang());
    }

    private Loaded read() throws IOException, InvalidConfigurationException {
        return new Loaded(readYaml("config.yml"), readYaml("lang.yml"));
    }

    private YamlConfiguration readYaml(String name) throws IOException, InvalidConfigurationException {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.load(new File(plugin.getDataFolder(), name));
        // значения по умолчанию из jar — если администратор удалил ключ, плагин не сломается
        try (InputStream in = plugin.getResource(name)) {
            if (in != null) {
                cfg.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        }
        return cfg;
    }

    private void ensureFile(String name) {
        if (!new File(plugin.getDataFolder(), name).exists()) {
            plugin.saveResource(name, false);
        }
    }

    private record Loaded(FileConfiguration config, FileConfiguration lang) {
    }
}
