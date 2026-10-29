package de.neptun.core;

import de.neptun.NeptunPlugin;
import de.neptun.ah.NeptunAH;
import de.neptun.chatitem.NeptunChatItem;
import de.neptun.messages.NeptunMessages;
import de.neptun.whitelist.NeptunWhitelist;
import de.neptun.minisk.MiniSk;
import de.neptun.social.NeptunSocial;
import de.neptun.trade.NeptunTrade;
import de.neptun.unknownkiller.NeptunUnknownKiller;
import de.neptun.worth.NeptunWorth;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * NeptunCore: ein Plugin, das Spawn/Homes/RTP, Auktionshaus, Trade, Social (Freunde/Settings),
 * UnknownKiller, MiniSk (Skripte, /discord, /store) und Worth (Worth/Sell/Shop) vereint.
 * Jedes Modul lässt sich in der config.yml einzeln abschalten.
 */
public final class NeptunCore extends JavaPlugin implements TabCompleter {

    private static final String PREFIX = "&8[&f&lNeptun&8] &f";

    /** Reihenfolge = Startreihenfolge. */
    private static final Map<String, Function<NeptunCore, Module>> FACTORIES = new LinkedHashMap<>();

    static {
        FACTORIES.put("neptun", NeptunPlugin::new);
        FACTORIES.put("worth", NeptunWorth::new);
        FACTORIES.put("ah", NeptunAH::new);
        FACTORIES.put("trade", NeptunTrade::new);
        FACTORIES.put("social", NeptunSocial::new);
        FACTORIES.put("unknownkiller", NeptunUnknownKiller::new);
        FACTORIES.put("minisk", MiniSk::new);
        FACTORIES.put("chatitem", NeptunChatItem::new);
        FACTORIES.put("messages", NeptunMessages::new);
        FACTORIES.put("whitelist", NeptunWhitelist::new);
    }

    /** Module, die ein anderes Plugin zwingend brauchen (fehlt es, wird das Modul sauber uebersprungen). */
    private static final Map<String, String> REQUIRES = Map.of("whitelist", "LuckPerms");

    private final Map<String, Module> modules = new LinkedHashMap<>();
    private final List<String> failed = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().addDefaults(Map.of("migrate-old-data", true));

        PluginCommand admin = getCommand("neptun");
        if (admin != null) {
            admin.setExecutor(this);
            admin.setTabCompleter(this);
        }

        for (Map.Entry<String, Function<NeptunCore, Module>> e : FACTORIES.entrySet()) {
            String id = e.getKey();
            if (!getConfig().getBoolean("modules." + id, true)) {
                getLogger().info("Modul '" + id + "' ist in der config.yml deaktiviert.");
                continue;
            }
            String required = REQUIRES.get(id);
            if (required != null && getServer().getPluginManager().getPlugin(required) == null) {
                getLogger().warning("Modul '" + id + "' braucht das Plugin " + required + " und wird uebersprungen.");
                continue;
            }
            Module m = null;
            try {
                m = e.getValue().apply(this);
                if (getConfig().getBoolean("migrate-old-data", true)) {
                    migrateLegacy(m);
                }
                for (String name : m.commands()) {
                    PluginCommand pc = getCommand(name);
                    if (pc == null) {
                        getLogger().warning("[" + id + "] Befehl /" + name + " fehlt in der plugin.yml");
                        continue;
                    }
                    pc.setExecutor(m);
                    if (m instanceof TabCompleter tc) {
                        pc.setTabCompleter(tc);
                    }
                }
                m.onEnable();
                modules.put(id, m);
            } catch (Throwable t) {
                failed.add(id);
                getLogger().severe("Modul '" + id + "' konnte nicht gestartet werden: " + t);
                t.printStackTrace();
                if (m != null) {
                    for (String name : m.commands()) {
                        PluginCommand pc = getCommand(name);
                        if (pc != null) {
                            pc.setExecutor((s, c, l, a) -> {
                                s.sendMessage(color(PREFIX + "&cDieses Modul ist wegen eines Fehlers nicht verfügbar."));
                                return true;
                            });
                            pc.setTabCompleter(null);
                        }
                    }
                }
            }
        }
        getLogger().info("NeptunCore aktiv. Module: " + String.join(", ", modules.keySet())
                + (failed.isEmpty() ? "" : " | FEHLGESCHLAGEN: " + String.join(", ", failed)));
    }

    @Override
    public void onDisable() {
        List<Module> reversed = new ArrayList<>(modules.values());
        java.util.Collections.reverse(reversed);
        for (Module m : reversed) {
            try {
                m.onDisable();
            } catch (Throwable t) {
                getLogger().warning("Fehler beim Beenden von '" + m.id() + "': " + t);
            }
        }
        modules.clear();
    }

    public Module module(String id) {
        return modules.get(id);
    }

    // ------------------------------------------------------------------ /neptun

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("neptun.admin")) {
            sender.sendMessage(color(PREFIX + "&cKeine Berechtigung."));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            for (Module m : modules.values()) {
                try {
                    m.onReload();
                } catch (Throwable t) {
                    sender.sendMessage(color(PREFIX + "&cReload von &f" + m.id() + " &cfehlgeschlagen: " + t.getMessage()));
                }
            }
            sender.sendMessage(color(PREFIX + "&aKonfiguration aller Module neu geladen. &7(Module an-/abschalten braucht einen Neustart.)"));
            return true;
        }
        sender.sendMessage(color(PREFIX + "&fNeptunCore &8v" + getPluginMeta().getVersion()));
        for (String id : FACTORIES.keySet()) {
            String state = modules.containsKey(id) ? "&aan" : failed.contains(id) ? "&cFehler" : "&7aus";
            sender.sendMessage(color("&8 - &f" + id + "&8: " + state));
        }
        sender.sendMessage(color("&f/neptun reload"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1 && "reload".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("reload");
        }
        return List.of();
    }

    // ------------------------------------------------------------------ Datenübernahme

    /**
     * Kopiert beim ersten Start die Dateien des früheren Einzel-Plugins
     * (z. B. plugins/NeptunAH/) in den neuen Modulordner. Die alten Dateien bleiben unangetastet.
     */
    private void migrateLegacy(Module m) {
        String legacy = m.legacyFolder();
        if (legacy == null) {
            return;
        }
        File target = new File(getDataFolder(), m.id());
        File source = new File(getDataFolder().getParentFile(), legacy);
        if (target.exists() || !source.isDirectory()) {
            return;
        }
        try (Stream<Path> walk = Files.walk(source.toPath())) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                Path dest = target.toPath().resolve(source.toPath().relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(p, dest);
                }
            }
            getLogger().info("[" + m.id() + "] Daten aus plugins/" + legacy + " übernommen.");
        } catch (IOException e) {
            getLogger().warning("[" + m.id() + "] Datenübernahme aus plugins/" + legacy + " fehlgeschlagen: " + e.getMessage());
        }
    }
}
