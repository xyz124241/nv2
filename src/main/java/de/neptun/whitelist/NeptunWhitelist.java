package de.neptun.whitelist;

import de.neptun.core.Module;
import de.neptun.core.NeptunCore;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.Permission;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

/**
 * Command-Whitelist und Rechte-Verwaltung pro LuckPerms-Rang mit GUI (früher NeptuneCommandWhitelist).
 * Befehl: /ncw. Benötigt LuckPerms.
 */
public final class NeptunWhitelist extends Module implements Listener, TabCompleter {

    private static final String PREFIX = "\u00a78[\u00a7bNCW\u00a78] \u00a77";

    static final List<String> BASIC_COMMANDS = List.of(
            "home", "homes", "sethome", "delhome", "removehome", "rtp", "wild", "betterrtp", "spawn",
            "msg", "tell", "w", "r", "reply", "tpa", "tpaccept", "tpdeny", "tpcancel", "tpahere", "help", "rules");
    static final List<String> BASIC_PERMS = List.of(
            "essentials.home", "essentials.sethome", "essentials.delhome", "essentials.spawn", "essentials.msg",
            "essentials.tpa", "essentials.tpaccept", "essentials.tpdeny", "essentials.tpahere", "betterrtp.use");

    private LuckPerms lp;
    private File dataFile;
    private boolean enabled = false;
    private boolean active = false;
    private String ownerGroup = "";
    private final Map<Category, String> managers = new EnumMap<>(Category.class);
    private final Map<String, Category> categories = new HashMap<>();
    private final Map<String, Set<String>> allowed = new HashMap<>();
    private final Map<UUID, Consumer<String>> pendingInput = new ConcurrentHashMap<>();
    private boolean refreshPending = false;

    public NeptunWhitelist(NeptunCore host) {
        super(host, "whitelist");
    }

    @Override
    public String[] commands() {
        return new String[]{"ncw"};
    }

    @Override
    public String legacyFolder() {
        return "NeptuneCommandWhitelist";
    }

    // ------------------------------------------------------------------ Lebenszyklus

    @Override
    public void onEnable() {
        lp = getServer().getServicesManager().load(LuckPerms.class);
        if (lp == null) {
            throw new IllegalStateException("LuckPerms nicht gefunden - Whitelist-Modul wird nicht gestartet.");
        }
        dataFile = new File(getDataFolder(), "data.yml");
        boolean firstStart = !dataFile.exists();
        loadData();
        if (firstStart) {
            allowedOf("default").addAll(BASIC_COMMANDS);
            writeData();
        }
        active = true;
        getServer().getPluginManager().registerEvents(this, host());
        getLogger().info("[whitelist] NCW aktiv. Whitelist ist "
                + (enabled ? "AN" : "AUS (erst konfigurieren, dann /ncw enable)"));
    }

    @Override
    public void onDisable() {
        active = false;
        if (dataFile != null) {
            writeData();
        }
    }

    @Override
    public void onReload() {
        loadData();
        scheduleRefresh();
    }

    void scheduleRefresh() {
        if (!active || !host().isEnabled() || refreshPending) {
            return;
        }
        refreshPending = true;
        Bukkit.getScheduler().runTaskLater(host(), () -> {
            refreshPending = false;
            for (Player pl : Bukkit.getOnlinePlayers()) {
                pl.updateCommands();
            }
        }, 5L);
    }

    // ------------------------------------------------------------------ Daten

    private void loadData() {
        enabled = false;
        ownerGroup = "";
        managers.clear();
        categories.clear();
        allowed.clear();
        if (!dataFile.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
        enabled = y.getBoolean("enabled", false);
        ownerGroup = y.getString("owner-group", "");
        ConfigurationSection m = y.getConfigurationSection("managers");
        if (m != null) {
            for (String k : m.getKeys(false)) {
                Category cat = Category.parse(k);
                String g = m.getString(k, "");
                if (cat == null || g.isEmpty()) {
                    continue;
                }
                managers.put(cat, g.toLowerCase(Locale.ROOT));
            }
        }
        ConfigurationSection c = y.getConfigurationSection("categories");
        if (c != null) {
            for (String g : c.getKeys(false)) {
                Category cat = Category.parse(c.getString(g));
                if (cat != null) {
                    categories.put(g.toLowerCase(Locale.ROOT), cat);
                }
            }
        }
        ConfigurationSection a = y.getConfigurationSection("allowed");
        if (a != null) {
            for (String g : a.getKeys(false)) {
                allowed.put(g.toLowerCase(Locale.ROOT), new TreeSet<>(a.getStringList(g)));
            }
        }
    }

    /** Speichert und aktualisiert die Befehlslisten der Spieler. */
    void saveData() {
        writeData();
        scheduleRefresh();
    }

    private void writeData() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("enabled", enabled);
        y.set("owner-group", ownerGroup);
        for (Map.Entry<Category, String> e : managers.entrySet()) {
            y.set("managers." + e.getKey().name(), e.getValue());
        }
        for (Map.Entry<String, Category> e : categories.entrySet()) {
            y.set("categories." + e.getKey(), e.getValue().name());
        }
        for (Map.Entry<String, Set<String>> e : allowed.entrySet()) {
            y.set("allowed." + e.getKey(), new ArrayList<>(e.getValue()));
        }
        try {
            getDataFolder().mkdirs();
            y.save(dataFile);
        } catch (IOException ex) {
            getLogger().severe("[whitelist] Konnte data.yml nicht speichern: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ LuckPerms / Logik

    Set<String> groupsOf(Player p) {
        User u = lp.getUserManager().getUser(p.getUniqueId());
        if (u == null) {
            return Collections.emptySet();
        }
        Set<String> s = new HashSet<>();
        for (Group g : u.getInheritedGroups(QueryOptions.nonContextual())) {
            s.add(g.getName().toLowerCase(Locale.ROOT));
        }
        s.add(u.getPrimaryGroup().toLowerCase(Locale.ROOT));
        return s;
    }

    boolean isAdmin(CommandSender s) {
        if (!(s instanceof Player p)) {
            return true;
        }
        if (p.hasPermission("ncw.admin")) {
            return true;
        }
        return !ownerGroup.isEmpty() && groupsOf(p).contains(ownerGroup);
    }

    boolean canManage(CommandSender s, String group) {
        if (isAdmin(s)) {
            return true;
        }
        if (!(s instanceof Player p)) {
            return false;
        }
        Category cat = categories.get(group.toLowerCase(Locale.ROOT));
        if (cat == null) {
            return false;
        }
        String mgr = managers.get(cat);
        return mgr != null && groupsOf(p).contains(mgr);
    }

    boolean canUse(CommandSender s) {
        if (isAdmin(s)) {
            return true;
        }
        if (!(s instanceof Player p)) {
            return false;
        }
        Set<String> g = groupsOf(p);
        for (String m : managers.values()) {
            if (g.contains(m)) {
                return true;
            }
        }
        return false;
    }

    Category categoryOf(String group) {
        return categories.get(group.toLowerCase(Locale.ROOT));
    }

    Set<String> allowedOf(String group) {
        return allowed.computeIfAbsent(group.toLowerCase(Locale.ROOT), k -> new TreeSet<>());
    }

    boolean hasAll(String group) {
        return allowedOf(group).contains("*");
    }

    boolean toggleCommand(String group, String command) {
        Set<String> s = allowedOf(group);
        String c = command.toLowerCase(Locale.ROOT);
        boolean now;
        if (s.contains(c)) {
            s.remove(c);
            now = false;
        } else {
            s.add(c);
            now = true;
        }
        saveData();
        return now;
    }

    void setCategory(String group, Category c) {
        if (c == null) {
            categories.remove(group.toLowerCase(Locale.ROOT));
        } else {
            categories.put(group.toLowerCase(Locale.ROOT), c);
        }
        saveData();
    }

    private Set<String> candidates(String label) {
        String l = label.toLowerCase(Locale.ROOT);
        Set<String> c = new HashSet<>();
        c.add(l);
        int i = l.indexOf(':');
        String base = i >= 0 ? l.substring(i + 1) : l;
        c.add(base);
        Command cmd = Bukkit.getCommandMap().getCommand(l);
        if (cmd == null) {
            cmd = Bukkit.getCommandMap().getCommand(base);
        }
        if (cmd != null) {
            c.add(cmd.getName().toLowerCase(Locale.ROOT));
            for (String a : cmd.getAliases()) {
                c.add(a.toLowerCase(Locale.ROOT));
            }
        }
        return c;
    }

    List<String> sourcesOf(String group) {
        String g = group.toLowerCase(Locale.ROOT);
        Set<String> out = new LinkedHashSet<>();
        out.add(g);
        out.add("default");
        Group lg = lp.getGroupManager().getGroup(g);
        if (lg != null) {
            for (Group parent : lg.getInheritedGroups(QueryOptions.nonContextual())) {
                out.add(parent.getName().toLowerCase(Locale.ROOT));
            }
            Category cat = categories.get(g);
            if (cat != null) {
                int w = lg.getWeight().orElse(0);
                for (Map.Entry<String, Category> e : categories.entrySet()) {
                    if (e.getValue() != cat) {
                        continue;
                    }
                    Group other = lp.getGroupManager().getGroup(e.getKey());
                    if (other == null || other.getWeight().orElse(0) >= w) {
                        continue;
                    }
                    out.add(e.getKey());
                }
            }
        }
        return new ArrayList<>(out);
    }

    String inheritedFrom(String group, List<String> sources, String command) {
        String self = group.toLowerCase(Locale.ROOT);
        for (String src : sources) {
            if (src.equals(self)) {
                continue;
            }
            Set<String> s = allowed.get(src);
            if (s == null || (!s.contains("*") && !s.contains(command))) {
                continue;
            }
            return src;
        }
        return null;
    }

    Set<String> mergedAllowed(Player p) {
        Set<String> srcs = new HashSet<>();
        srcs.add("default");
        for (String g : groupsOf(p)) {
            srcs.addAll(sourcesOf(g));
        }
        Set<String> merged = new HashSet<>();
        for (String s : srcs) {
            Set<String> set = allowed.get(s);
            if (set != null) {
                merged.addAll(set);
            }
        }
        return merged;
    }

    boolean allows(Set<String> merged, String label) {
        if (merged.contains("*")) {
            return true;
        }
        for (String c : candidates(label)) {
            if (c.equals("ncw") || merged.contains(c)) {
                return true;
            }
        }
        return false;
    }

    boolean isAllowed(Player p, String label) {
        if (!enabled || isAdmin(p)) {
            return true;
        }
        return allows(mergedAllowed(p), label);
    }

    Map<String, String> allCommands() {
        Map<String, String> out = new TreeMap<>();
        for (Map.Entry<String, Command> e : Bukkit.getCommandMap().getKnownCommands().entrySet()) {
            String name = e.getKey();
            if (name.contains(":")) {
                continue;
            }
            Command c = e.getValue();
            String owner = c instanceof PluginCommand pc ? pc.getPlugin().getName() : "Minecraft/Server";
            out.put(name.toLowerCase(Locale.ROOT), owner);
        }
        return out;
    }

    List<String> allPermissions() {
        return Bukkit.getPluginManager().getPermissions().stream()
                .map(Permission::getName)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .distinct().sorted().collect(Collectors.toList());
    }

    boolean hasPerm(String group, String node) {
        Group g = lp.getGroupManager().getGroup(group);
        if (g == null) {
            return false;
        }
        return g.getNodes(NodeType.PERMISSION).stream()
                .anyMatch(n -> n.getKey().equalsIgnoreCase(node) && n.getValue() && !n.hasExpiry());
    }

    void setPerm(String group, String node, Boolean value) {
        Group g = lp.getGroupManager().getGroup(group);
        if (g == null) {
            return;
        }
        g.data().clear(NodeType.PERMISSION.predicate(n -> n.getKey().equalsIgnoreCase(node) && n.getContexts().isEmpty()));
        if (value != null) {
            g.data().add(Node.builder(node).value(value).build());
        }
        lp.getGroupManager().saveGroup(g);
        scheduleRefresh();
    }

    void togglePerm(String group, String node) {
        setPerm(group, node, hasPerm(group, node) ? null : Boolean.TRUE);
    }

    Set<String> directPerms(String group) {
        Group g = lp.getGroupManager().getGroup(group);
        if (g == null) {
            return Collections.emptySet();
        }
        Set<String> out = new HashSet<>();
        for (PermissionNode n : g.getNodes(NodeType.PERMISSION)) {
            if (n.getValue() && !n.hasExpiry()) {
                out.add(n.getKey().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    int syncInheritance() {
        int changes = 0;
        for (Group g : lp.getGroupManager().getLoadedGroups()) {
            if (!g.getName().equalsIgnoreCase("default") && addParent(g, "default")) {
                changes++;
            }
        }
        for (Category cat : Category.values()) {
            List<Group> list = new ArrayList<>();
            for (Map.Entry<String, Category> e : categories.entrySet()) {
                if (e.getValue() != cat) {
                    continue;
                }
                Group g = lp.getGroupManager().getGroup(e.getKey());
                if (g != null) {
                    list.add(g);
                }
            }
            list.sort(Comparator.comparingInt((Group g) -> g.getWeight().orElse(0)));
            for (int i = 1; i < list.size(); i++) {
                Group lower = list.get(i - 1);
                Group higher = list.get(i);
                if (higher.getWeight().orElse(0) <= lower.getWeight().orElse(0)) {
                    continue;
                }
                if (addParent(higher, lower.getName())) {
                    changes++;
                }
            }
        }
        return changes;
    }

    private boolean addParent(Group g, String parent) {
        if (g.getName().equalsIgnoreCase(parent)) {
            return false;
        }
        InheritanceNode n = InheritanceNode.builder(parent).build();
        if (g.data().add(n).wasSuccessful()) {
            lp.getGroupManager().saveGroup(g);
            scheduleRefresh();
            return true;
        }
        return false;
    }

    void applyDefaults() {
        allowedOf("default").addAll(BASIC_COMMANDS);
        saveData();
        if (lp.getGroupManager().getGroup("default") != null) {
            for (String perm : BASIC_PERMS) {
                setPerm("default", perm, Boolean.TRUE);
            }
        }
    }

    void awaitInput(Player p, Consumer<String> callback) {
        pendingInput.put(p.getUniqueId(), callback);
    }

    // ------------------------------------------------------------------ Events

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreprocess(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage().trim();
        String label = msg.startsWith("/") ? msg.substring(1) : msg;
        int sp = label.indexOf(' ');
        if (sp >= 0) {
            label = label.substring(0, sp);
        }
        if (label.isEmpty()) {
            return;
        }
        if (!isAllowed(e.getPlayer(), label)) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(Component.text("Unknown command. Type \"/help\" for help.", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSend(PlayerCommandSendEvent e) {
        if (!enabled || isAdmin(e.getPlayer())) {
            return;
        }
        Set<String> merged = mergedAllowed(e.getPlayer());
        e.getCommands().removeIf(name -> !allows(merged, name));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Consumer<String> cb = pendingInput.remove(e.getPlayer().getUniqueId());
        if (cb == null) {
            return;
        }
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Bukkit.getScheduler().runTask(host(), () -> cb.accept(text));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pendingInput.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu menu)) {
            return;
        }
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) {
            return;
        }
        menu.click(e.getRawSlot());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ Befehl /ncw

    private void msg(CommandSender s, String m) {
        s.sendMessage(PREFIX + m);
    }

    private boolean groupExists(CommandSender s, String name) {
        if (lp.getGroupManager().getGroup(name) != null) {
            return true;
        }
        msg(s, "\u00a7cDie LuckPerms-Gruppe \u00a7f" + name + " \u00a7cgibt es nicht.");
        return false;
    }

    private void help(CommandSender s) {
        msg(s, "\u00a7bNeptune Command Whitelist");
        s.sendMessage("\u00a7f/ncw gui \u00a77- Menue oeffnen");
        s.sendMessage("\u00a7f/ncw list \u00a77- Raenge und Kategorien");
        s.sendMessage("\u00a7f/ncw info <rang> \u00a77- Freigaben eines Rangs");
        s.sendMessage("\u00a7f/ncw allow <rang> <befehl|*> \u00a77- Befehl freigeben");
        s.sendMessage("\u00a7f/ncw deny <rang> <befehl|*> \u00a77- Befehl sperren");
        s.sendMessage("\u00a7f/ncw perm <rang> <permission> <true|false|unset> \u00a77- LuckPerms-Recht setzen");
        if (isAdmin(s)) {
            s.sendMessage("\u00a7f/ncw category <rang> <staff|buy|other|none> \u00a77- Kategorie");
            s.sendMessage("\u00a7f/ncw manager <staff|buy|other> <rang|none> \u00a77- Oberster Rang der Kategorie");
            s.sendMessage("\u00a7f/ncw owner <rang|none> \u00a77- Rang, der alles darf");
            s.sendMessage("\u00a7f/ncw sync \u00a77- Vererbung herstellen (default -> alle, Kategorie nach Gewicht)");
            s.sendMessage("\u00a7f/ncw defaults \u00a77- Grund-Befehle setzen (home, rtp, ...)");
            s.sendMessage("\u00a7f/ncw enable|disable \u00a77- Whitelist an/aus");
            s.sendMessage("\u00a7f/ncw reload \u00a77- Daten neu laden");
        }
    }

    private boolean adminOnly(CommandSender s) {
        if (isAdmin(s)) {
            return true;
        }
        msg(s, "\u00a7cNur Admins duerfen das aendern.");
        return false;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!canUse(s)) {
            msg(s, "\u00a7cDazu hast du keine Berechtigung.");
            return true;
        }
        String sub = a.length == 0 ? "gui" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> help(s);
            case "gui", "menu" -> {
                if (s instanceof Player p) {
                    new Menu(this, p).openGroups(0);
                } else {
                    msg(s, "\u00a7cDas Menue gibt es nur im Spiel.");
                }
            }
            case "list" -> {
                msg(s, "\u00a77Raenge:");
                for (Group g : sortedGroups()) {
                    if (!canManage(s, g.getName())) {
                        continue;
                    }
                    Category cat = categoryOf(g.getName());
                    s.sendMessage(" \u00a7f" + g.getName() + " \u00a78| \u00a77" + (cat == null ? "keine Kategorie" : cat.label)
                            + " \u00a78| \u00a77" + allowedOf(g.getName()).size() + " Eintraege");
                }
                msg(s, "\u00a77Whitelist: " + (enabled ? "\u00a7aAN" : "\u00a7cAUS") + " \u00a78| \u00a77Owner: \u00a7f"
                        + (ownerGroup.isEmpty() ? "-" : ownerGroup));
                for (Category cat : Category.values()) {
                    s.sendMessage(" \u00a77" + cat.label + ": \u00a7f" + managers.getOrDefault(cat, "-"));
                }
            }
            case "info" -> {
                if (a.length < 2 || !groupExists(s, a[1])) {
                    return true;
                }
                if (!canManage(s, a[1])) {
                    msg(s, "\u00a7cDiesen Rang darfst du nicht verwalten.");
                    return true;
                }
                Category cat = categoryOf(a[1]);
                msg(s, "\u00a7f" + a[1] + " \u00a78| \u00a77" + (cat == null ? "keine Kategorie" : cat.label));
                Set<String> set = allowedOf(a[1]);
                s.sendMessage(" \u00a77Befehle: \u00a7f" + (set.isEmpty() ? "-" : String.join(", ", set)));
            }
            case "allow", "deny" -> {
                if (a.length < 3) {
                    msg(s, "\u00a77Nutzung: /ncw " + sub + " <rang> <befehl|*>");
                    return true;
                }
                if (!groupExists(s, a[1])) {
                    return true;
                }
                if (!canManage(s, a[1])) {
                    msg(s, "\u00a7cDiesen Rang darfst du nicht verwalten.");
                    return true;
                }
                String cmdName = a[2].toLowerCase(Locale.ROOT);
                if (cmdName.startsWith("/")) {
                    cmdName = cmdName.substring(1);
                }
                Set<String> set = allowedOf(a[1]);
                boolean allow = sub.equals("allow");
                if (allow) {
                    set.add(cmdName);
                } else {
                    set.remove(cmdName);
                }
                saveData();
                msg(s, "\u00a7f/" + cmdName + " \u00a77fuer \u00a7f" + a[1] + (allow ? " \u00a7afreigegeben" : " \u00a7cgesperrt"));
            }
            case "perm" -> {
                if (a.length < 4) {
                    msg(s, "\u00a77Nutzung: /ncw perm <rang> <permission> <true|false|unset>");
                    return true;
                }
                if (!groupExists(s, a[1])) {
                    return true;
                }
                if (!canManage(s, a[1])) {
                    msg(s, "\u00a7cDiesen Rang darfst du nicht verwalten.");
                    return true;
                }
                Boolean v;
                switch (a[3].toLowerCase(Locale.ROOT)) {
                    case "true" -> v = Boolean.TRUE;
                    case "false" -> v = Boolean.FALSE;
                    case "unset" -> v = null;
                    default -> {
                        msg(s, "\u00a7cWert muss true, false oder unset sein.");
                        return true;
                    }
                }
                setPerm(a[1], a[2], v);
                msg(s, "\u00a77Recht \u00a7f" + a[2] + " \u00a77fuer \u00a7f" + a[1] + " \u00a77-> \u00a7f" + (v == null ? "entfernt" : v.toString()));
            }
            case "category" -> {
                if (!adminOnly(s)) {
                    return true;
                }
                if (a.length < 3) {
                    msg(s, "\u00a77Nutzung: /ncw category <rang> <staff|buy|other|none>");
                    return true;
                }
                if (!groupExists(s, a[1])) {
                    return true;
                }
                if (a[2].equalsIgnoreCase("none")) {
                    setCategory(a[1], null);
                    msg(s, "\u00a7f" + a[1] + " \u00a77hat jetzt keine Kategorie.");
                    return true;
                }
                Category cat = Category.parse(a[2]);
                if (cat == null) {
                    msg(s, "\u00a7cKategorie: staff, buy, other oder none.");
                    return true;
                }
                setCategory(a[1], cat);
                msg(s, "\u00a7f" + a[1] + " \u00a77ist jetzt in \u00a7f" + cat.label);
            }
            case "manager" -> {
                if (!adminOnly(s)) {
                    return true;
                }
                if (a.length < 3) {
                    msg(s, "\u00a77Nutzung: /ncw manager <staff|buy|other> <rang|none>");
                    return true;
                }
                Category cat = Category.parse(a[1]);
                if (cat == null) {
                    msg(s, "\u00a7cKategorie: staff, buy oder other.");
                    return true;
                }
                if (a[2].equalsIgnoreCase("none")) {
                    managers.remove(cat);
                } else {
                    if (!groupExists(s, a[2])) {
                        return true;
                    }
                    managers.put(cat, a[2].toLowerCase(Locale.ROOT));
                }
                saveData();
                msg(s, "\u00a77Oberster Rang fuer \u00a7f" + cat.label + "\u00a77: \u00a7f" + managers.getOrDefault(cat, "-"));
            }
            case "owner" -> {
                if (!adminOnly(s)) {
                    return true;
                }
                if (a.length < 2) {
                    msg(s, "\u00a77Nutzung: /ncw owner <rang|none>");
                    return true;
                }
                if (a[1].equalsIgnoreCase("none")) {
                    ownerGroup = "";
                } else {
                    if (!groupExists(s, a[1])) {
                        return true;
                    }
                    ownerGroup = a[1].toLowerCase(Locale.ROOT);
                }
                saveData();
                msg(s, "\u00a77Owner-Rang: \u00a7f" + (ownerGroup.isEmpty() ? "-" : ownerGroup));
            }
            case "enable", "disable" -> {
                if (!adminOnly(s)) {
                    return true;
                }
                enabled = sub.equals("enable");
                saveData();
                msg(s, "\u00a77Whitelist ist jetzt " + (enabled ? "\u00a7aAN" : "\u00a7cAUS"));
                if (enabled) {
                    msg(s, "\u00a77Tipp: Gib dem Rang 'default' z.B. /spawn, /msg usw., sonst kann niemand etwas tippen.");
                }
            }
            case "sync" -> {
                if (!adminOnly(s)) {
                    return true;
                }
                int n = syncInheritance();
                msg(s, "\u00a77Vererbung abgeglichen (\u00a7f" + n + " \u00a77neue Verknuepfungen).");
                msg(s, "\u00a77Tipp: Staff-Raenge brauchen ein Gewicht, z.B. /lp group owner setweight 100");
            }
            case "defaults" -> {
                if (!adminOnly(s)) {
                    return true;
                }
                applyDefaults();
                msg(s, "\u00a77Grund-Befehle (home, sethome, delhome, rtp, spawn, msg, tpa ...) fuer \u00a7fdefault \u00a77gesetzt.");
            }
            case "reload" -> {
                if (!adminOnly(s)) {
                    return true;
                }
                loadData();
                scheduleRefresh();
                msg(s, "\u00a77Daten neu geladen.");
            }
            default -> help(s);
        }
        return true;
    }

    List<Group> sortedGroups() {
        List<Group> list = new ArrayList<>(lp.getGroupManager().getLoadedGroups());
        list.sort(Comparator.comparingInt((Group g) -> g.getWeight().orElse(0)).reversed().thenComparing(Group::getName));
        return list;
    }

    // ------------------------------------------------------------------ Tab-Completion

    private List<String> filter(Collection<String> options, String typed) {
        String t = typed.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(t)).limit(80).collect(Collectors.toList());
    }

    private List<String> groupNames() {
        return lp.getGroupManager().getLoadedGroups().stream().map(Group::getName).sorted().collect(Collectors.toList());
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] a) {
        if (!canUse(s)) {
            return Collections.emptyList();
        }
        if (a.length == 1) {
            List<String> subs = new ArrayList<>(List.of("gui", "help", "list", "info", "allow", "deny", "perm"));
            if (isAdmin(s)) {
                subs.addAll(List.of("category", "manager", "owner", "sync", "defaults", "enable", "disable", "reload"));
            }
            return filter(subs, a[0]);
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (a.length == 2) {
            switch (sub) {
                case "info", "allow", "deny", "perm", "category":
                    return filter(groupNames(), a[1]);
                case "manager":
                    return filter(List.of("staff", "buy", "other"), a[1]);
                case "owner": {
                    List<String> o = new ArrayList<>(groupNames());
                    o.add("none");
                    return filter(o, a[1]);
                }
                default:
                    return Collections.emptyList();
            }
        }
        if (a.length == 3) {
            switch (sub) {
                case "allow": {
                    List<String> o = new ArrayList<>(allCommands().keySet());
                    o.add("*");
                    return filter(o, a[2]);
                }
                case "deny":
                    return filter(allowedOf(a[1]), a[2]);
                case "perm":
                    return filter(allPermissions(), a[2]);
                case "category":
                    return filter(List.of("staff", "buy", "other", "none"), a[2]);
                case "manager": {
                    List<String> o = new ArrayList<>(groupNames());
                    o.add("none");
                    return filter(o, a[2]);
                }
                default:
                    return Collections.emptyList();
            }
        }
        if (a.length == 4 && sub.equals("perm")) {
            return filter(List.of("true", "false", "unset"), a[3]);
        }
        return Collections.emptyList();
    }

    // ------------------------------------------------------------------ Kategorien

    public enum Category {
        STAFF("Staff-Mitglieder", Material.DIAMOND),
        BUY("Buy-Ranks", Material.EMERALD),
        OTHER("Sonstige (Media, Friend, ...)", Material.AMETHYST_SHARD);

        public final String label;
        public final Material icon;

        Category(String label, Material icon) {
            this.label = label;
            this.icon = icon;
        }

        public static Category parse(String s) {
            if (s == null) {
                return null;
            }
            return switch (s.toLowerCase(Locale.ROOT)) {
                case "staff", "team", "mitglieder" -> STAFF;
                case "buy", "buyranks", "kauf", "shop" -> BUY;
                case "other", "sonstige", "andere", "media" -> OTHER;
                default -> null;
            };
        }
    }

    // ------------------------------------------------------------------ GUI

    static final class Menu implements InventoryHolder {
        private static final int PAGE_SIZE = 45;
        private final NeptunWhitelist plugin;
        private final Player viewer;
        private Inventory inv;
        private final Map<Integer, Runnable> handlers = new HashMap<>();
        private String search = "";

        Menu(NeptunWhitelist plugin, Player viewer) {
            this.plugin = plugin;
            this.viewer = viewer;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }

        void click(int slot) {
            Runnable r = handlers.get(slot);
            if (r != null) {
                r.run();
            }
        }

        private static Component text(String s, NamedTextColor color) {
            return Component.text(s, (TextColor) color).decoration(TextDecoration.ITALIC, false);
        }

        private static ItemStack item(Material m, String name, NamedTextColor color, String... lore) {
            ItemStack it = new ItemStack(m);
            ItemMeta meta = it.getItemMeta();
            meta.displayName(text(name, color));
            List<Component> l = new ArrayList<>();
            for (String s : lore) {
                l.add(text(s, NamedTextColor.GRAY));
            }
            meta.lore(l);
            it.setItemMeta(meta);
            return it;
        }

        private void reset(int size, String title) {
            handlers.clear();
            inv = Bukkit.createInventory(this, size, Component.text(title));
        }

        private void set(int slot, ItemStack it, Runnable onClick) {
            inv.setItem(slot, it);
            if (onClick != null) {
                handlers.put(slot, onClick);
            }
        }

        private boolean stillAllowed(String group) {
            if (plugin.canManage(viewer, group)) {
                return true;
            }
            viewer.sendMessage("\u00a78[\u00a7bNCW\u00a78] \u00a7cDiesen Rang darfst du nicht verwalten.");
            viewer.closeInventory();
            return false;
        }

        private void show() {
            viewer.openInventory(inv);
        }

        private void paged(String title, List<ItemStack> items, List<Runnable> actions, int page, IntConsumer reopen,
                           Runnable back, ItemStack extra, Runnable extraAction, boolean searchable) {
            int pages = Math.max(1, (items.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            int p = Math.min(Math.max(page, 0), pages - 1);
            reset(54, title + " \u00a78(" + (p + 1) + "/" + pages + ")");
            int start = p * PAGE_SIZE;
            for (int i = 0; i < PAGE_SIZE && start + i < items.size(); i++) {
                set(i, items.get(start + i), actions.get(start + i));
            }
            ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY);
            for (int i = PAGE_SIZE; i < 54; i++) {
                set(i, filler, null);
            }
            if (p > 0) {
                set(45, item(Material.ARROW, "Vorherige Seite", NamedTextColor.YELLOW), () -> reopen.accept(p - 1));
            }
            if (back != null) {
                set(49, item(Material.BARRIER, "Zurueck", NamedTextColor.RED), back);
            }
            if (p < pages - 1) {
                set(53, item(Material.ARROW, "Naechste Seite", NamedTextColor.YELLOW), () -> reopen.accept(p + 1));
            }
            if (extra != null) {
                set(47, extra, extraAction);
            }
            if (searchable) {
                set(51, item(Material.COMPASS, search.isEmpty() ? "Suchen" : "Suche: " + search, NamedTextColor.AQUA,
                        "Klicken, dann Begriff in den Chat schreiben", "z.B. essentials oder home",
                        search.isEmpty() ? "" : "Im Chat '-' schreiben, um die Suche zu loeschen"), () -> {
                    viewer.closeInventory();
                    viewer.sendMessage("\u00a78[\u00a7bNCW\u00a78] \u00a77Schreibe den Suchbegriff in den Chat \u00a78(\u00a7f-\u00a78 = Suche loeschen, \u00a7fabbrechen\u00a78 = nichts aendern)\u00a77:");
                    plugin.awaitInput(viewer, text -> {
                        if (!text.equalsIgnoreCase("abbrechen")) {
                            search = text.equals("-") ? "" : text.toLowerCase(Locale.ROOT);
                        }
                        reopen.accept(0);
                    });
                });
            }
            show();
        }

        private boolean matches(String... fields) {
            if (search.isEmpty()) {
                return true;
            }
            for (String f : fields) {
                if (f != null && f.toLowerCase(Locale.ROOT).contains(search)) {
                    return true;
                }
            }
            return false;
        }

        void openGroups(int page) {
            List<ItemStack> items = new ArrayList<>();
            List<Runnable> actions = new ArrayList<>();
            for (Group g : plugin.sortedGroups()) {
                String name = g.getName();
                if (!plugin.canManage(viewer, name)) {
                    continue;
                }
                Category cat = plugin.categoryOf(name);
                Material mat = cat == null ? Material.PAPER : cat.icon;
                items.add(item(mat, name, NamedTextColor.AQUA,
                        "Kategorie: " + (cat == null ? "keine" : cat.label),
                        "Freigaben: " + (plugin.hasAll(name) ? "ALLE" : String.valueOf(plugin.allowedOf(name).size())),
                        "Klicken zum Bearbeiten"));
                actions.add(() -> openGroup(name));
            }
            paged("NCW - Raenge", items, actions, page, this::openGroups, null, null, null, false);
        }

        void openGroup(String group) {
            if (!stillAllowed(group)) {
                return;
            }
            reset(27, "NCW - " + group);
            Category cat = plugin.categoryOf(group);
            set(10, item(Material.COMMAND_BLOCK, "Befehle", NamedTextColor.GREEN, "Befehle aller Plugins", "freigeben oder sperren"), () -> {
                search = "";
                openCommands(group, 0, 0);
            });
            set(12, item(Material.WRITABLE_BOOK, "Berechtigungen", NamedTextColor.GOLD, "LuckPerms-Rechte aller Plugins", "setzen oder entfernen"), () -> {
                search = "";
                openPerms(group, 0, 0);
            });
            boolean all = plugin.hasAll(group);
            set(14, item(all ? Material.LIME_CONCRETE : Material.RED_CONCRETE, "Alle Befehle: " + (all ? "AN" : "AUS"),
                    all ? NamedTextColor.GREEN : NamedTextColor.RED, "Klicken zum Umschalten"), () -> {
                if (!stillAllowed(group)) {
                    return;
                }
                plugin.toggleCommand(group, "*");
                openGroup(group);
            });
            boolean admin = plugin.isAdmin(viewer);
            Runnable cycle = admin ? () -> {
                Category[] all2 = Category.values();
                Category next = cat == null ? all2[0] : (cat.ordinal() + 1 < all2.length ? all2[cat.ordinal() + 1] : null);
                plugin.setCategory(group, next);
                openGroup(group);
            } : null;
            set(16, item(cat == null ? Material.PAPER : cat.icon, "Kategorie: " + (cat == null ? "keine" : cat.label),
                    NamedTextColor.LIGHT_PURPLE, admin ? "Klicken zum Wechseln" : "Nur Admins koennen das aendern"), cycle);
            set(22, item(Material.BARRIER, "Zurueck", NamedTextColor.RED), () -> openGroups(0));
            show();
        }

        private void openCommands(String group, int page, int filterIdx) {
            if (!stillAllowed(group)) {
                return;
            }
            Map<String, String> cmds = new TreeMap<>(plugin.allCommands());
            for (String s : plugin.allowedOf(group)) {
                if (!s.equals("*")) {
                    cmds.putIfAbsent(s, "Eigener Eintrag");
                }
            }
            List<String> owners = new ArrayList<>(new TreeSet<>(cmds.values()));
            owners.add(0, "Alle Plugins");
            int fi = Math.floorMod(filterIdx, owners.size());
            String filter = owners.get(fi);
            List<ItemStack> items = new ArrayList<>();
            List<Runnable> actions = new ArrayList<>();
            Set<String> set = plugin.allowedOf(group);
            boolean all = set.contains("*");
            List<String> sources = plugin.sourcesOf(group);
            for (Map.Entry<String, String> e : cmds.entrySet()) {
                if (fi != 0 && !e.getValue().equals(filter)) {
                    continue;
                }
                String cmd = e.getKey();
                if (!matches(cmd, e.getValue())) {
                    continue;
                }
                boolean own = all || set.contains(cmd);
                String from = own ? null : plugin.inheritedFrom(group, sources, cmd);
                boolean on = own || from != null;
                if (from != null) {
                    items.add(item(Material.GREEN_DYE, "/" + cmd, NamedTextColor.DARK_GREEN, "Plugin: " + e.getValue(),
                            "Geerbt von: " + from, "Dort aendern, nicht hier"));
                    actions.add(() -> viewer.sendMessage("\u00a78[\u00a7bNCW\u00a78] \u00a77/" + cmd + " kommt von \u00a7f" + from
                            + "\u00a77. Aendere es dort, dann gilt es automatisch ueberall."));
                    continue;
                }
                items.add(item(on ? Material.LIME_DYE : Material.GRAY_DYE, "/" + cmd, on ? NamedTextColor.GREEN : NamedTextColor.RED,
                        "Plugin: " + e.getValue(), on ? "Freigegeben" : "Gesperrt", "Klicken zum Umschalten"));
                actions.add(() -> {
                    if (!stillAllowed(group)) {
                        return;
                    }
                    plugin.toggleCommand(group, cmd);
                    openCommands(group, page, fi);
                });
            }
            ItemStack f = item(Material.HOPPER, "Filter: " + filter, NamedTextColor.YELLOW, "Klicken fuer naechstes Plugin");
            paged("NCW - Befehle " + group, items, actions, page, pg -> openCommands(group, pg, fi),
                    () -> openGroup(group), f, () -> openCommands(group, 0, fi + 1), true);
        }

        private void openPerms(String group, int page, int filterIdx) {
            if (!stillAllowed(group)) {
                return;
            }
            List<String> perms = plugin.allPermissions();
            Set<String> prefixes = new TreeSet<>();
            for (String p : perms) {
                int i = p.indexOf('.');
                prefixes.add(i > 0 ? p.substring(0, i) : p);
            }
            List<String> filters = new ArrayList<>(prefixes);
            filters.add(0, "Alle");
            int fi = Math.floorMod(filterIdx, filters.size());
            String filter = filters.get(fi);
            List<ItemStack> items = new ArrayList<>();
            List<Runnable> actions = new ArrayList<>();
            Set<String> own = plugin.directPerms(group);
            Map<String, Set<String>> parentPerms = new LinkedHashMap<>();
            for (String src : plugin.sourcesOf(group)) {
                if (!src.equalsIgnoreCase(group)) {
                    parentPerms.put(src, plugin.directPerms(src));
                }
            }
            for (String node : perms) {
                if (fi != 0) {
                    int i = node.indexOf('.');
                    String pre = i > 0 ? node.substring(0, i) : node;
                    if (!pre.equals(filter)) {
                        continue;
                    }
                }
                if (!matches(node)) {
                    continue;
                }
                String from = null;
                if (!own.contains(node)) {
                    for (Map.Entry<String, Set<String>> pe : parentPerms.entrySet()) {
                        if (pe.getValue().contains(node)) {
                            from = pe.getKey();
                            break;
                        }
                    }
                }
                if (from != null) {
                    String src = from;
                    items.add(item(Material.GREEN_DYE, node, NamedTextColor.DARK_GREEN, "Geerbt von: " + src, "Dort aendern, nicht hier"));
                    actions.add(() -> viewer.sendMessage(PREFIX + node + " kommt von \u00a7f" + src
                            + "\u00a77. Aendere es dort, dann gilt es automatisch ueberall."));
                    continue;
                }
                boolean on = own.contains(node);
                items.add(item(on ? Material.LIME_DYE : Material.GRAY_DYE, node, on ? NamedTextColor.GREEN : NamedTextColor.RED,
                        on ? "Gesetzt (true)" : "Nicht gesetzt", "Klicken zum Umschalten"));
                actions.add(() -> {
                    if (!stillAllowed(group)) {
                        return;
                    }
                    plugin.togglePerm(group, node);
                    openPerms(group, page, fi);
                });
            }
            ItemStack f = item(Material.HOPPER, "Filter: " + filter, NamedTextColor.YELLOW, "Klicken fuer naechsten Bereich");
            paged("NCW - Rechte " + group, items, actions, page, pg -> openPerms(group, pg, fi),
                    () -> openGroup(group), f, () -> openPerms(group, 0, fi + 1), true);
        }
    }
}
