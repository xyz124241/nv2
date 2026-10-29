package de.neptun.core;

import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Logger;

/**
 * Basisklasse für alle Neptun-Module. Bietet die gleichen Hilfsmethoden wie JavaPlugin
 * (getConfig, getDataFolder, getLogger, getCommand ...), damit der Code der ehemals
 * eigenständigen Plugins fast unverändert weiterläuft. Jedes Modul bekommt einen eigenen
 * Ordner plugins/NeptunCore/&lt;id&gt;/ mit eigener config.yml.
 */
public abstract class Module implements CommandExecutor {

    private final NeptunCore host;
    private final String id;
    private final File dataFolder;
    private FileConfiguration config;

    protected Module(NeptunCore host, String id) {
        this.host = host;
        this.id = id;
        this.dataFolder = new File(host.getDataFolder(), id);
    }

    /** Wird beim Start aufgerufen, nachdem die Befehle diesem Modul zugewiesen wurden. */
    public abstract void onEnable();

    public void onDisable() {
    }

    /** /neptun reload. Standard: nur die config.yml des Moduls neu lesen. */
    public void onReload() {
        reloadConfig();
    }

    /** Befehle aus der plugin.yml, die dieses Modul bedient. */
    public String[] commands() {
        return new String[0];
    }

    /** Ordnername des früheren Einzel-Plugins unter plugins/ (für die Datenübernahme). */
    public String legacyFolder() {
        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return false;
    }

    public final String id() {
        return id;
    }

    /** Das Haupt-Plugin: wird für Scheduler, Events, NamespacedKey usw. gebraucht. */
    public final NeptunCore host() {
        return host;
    }

    public final Server getServer() {
        return host.getServer();
    }

    public final Logger getLogger() {
        return host.getLogger();
    }

    public final PluginCommand getCommand(String name) {
        return host.getCommand(name);
    }

    public final File getDataFolder() {
        dataFolder.mkdirs();
        return dataFolder;
    }

    // ------------------------------------------------------------------ config

    public final void saveDefaultConfig() {
        File f = new File(getDataFolder(), "config.yml");
        if (!f.exists()) {
            saveResource("config.yml", false);
        }
    }

    public final FileConfiguration getConfig() {
        if (config == null) {
            reloadConfig();
        }
        return config;
    }

    public final void reloadConfig() {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "config.yml"));
        try (InputStream in = host.getResource("modules/" + id + "/config.yml")) {
            if (in != null) {
                cfg.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException ignored) {
            // ohne Defaults weitermachen
        }
        config = cfg;
    }

    public final void saveConfig() {
        try {
            getConfig().save(new File(getDataFolder(), "config.yml"));
        } catch (IOException e) {
            getLogger().warning("[" + id + "] config.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    /** Kopiert eine Datei aus modules/&lt;id&gt;/&lt;path&gt; im JAR in den Modulordner. */
    public final void saveResource(String path, boolean replace) {
        File out = new File(getDataFolder(), path);
        if (out.exists() && !replace) {
            return;
        }
        try (InputStream in = host.getResource("modules/" + id + "/" + path)) {
            if (in == null) {
                getLogger().warning("[" + id + "] Ressource fehlt im JAR: " + path);
                return;
            }
            File parent = out.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            Files.copy(in, out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            getLogger().warning("[" + id + "] Konnte " + path + " nicht speichern: " + e.getMessage());
        }
    }
}
