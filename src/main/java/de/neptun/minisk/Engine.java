package de.neptun.minisk;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class Engine {
    private final MiniSk plugin;
    private final Map<String, Menu> menus = new HashMap<>();

    public Engine(MiniSk plugin) {
        this.plugin = plugin;
    }

    /** Commands created by scripts. No permission set => everyone can use them. */
    private static class SkCommand extends Command {
        private final Engine engine;
        private final List<Node> actions;

        SkCommand(Engine engine, String name, String desc, String usage, List<String> aliases, List<Node> actions) {
            super(name, desc, usage, aliases);
            this.engine = engine;
            this.actions = actions;
            setPermission(null);
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            engine.run(actions, sender, args);
            return true;
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
            return Collections.emptyList();
        }
    }

    public int load() {
        CommandMap map = Bukkit.getCommandMap();
        map.getKnownCommands().values().removeIf(c -> c instanceof SkCommand);
        menus.clear();

        File dir = new File(plugin.getDataFolder(), "scripts");
        dir.mkdirs();
        int count = 0;
        try (Stream<Path> files = Files.walk(dir.toPath())) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".sk"))::iterator) {
                try {
                    for (Node n : Util.parse(p)) {
                        if (n.text.toLowerCase().startsWith("command ")) {
                            registerCommand(map, n);
                            count++;
                        } else if (n.text.toLowerCase().startsWith("menu ")) {
                            registerMenu(n);
                            count++;
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Error in " + p.getFileName() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read scripts: " + e.getMessage());
        }
        Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
        return count;
    }

    private void registerCommand(CommandMap map, Node n) {
        String head = n.text.substring(8).trim();
        if (head.endsWith(":")) head = head.substring(0, head.length() - 1).trim();
        if (head.startsWith("/")) head = head.substring(1);
        String name = head.split(" ")[0].toLowerCase();
        String desc = "Script command", usage = "/" + name;
        List<String> aliases = new ArrayList<>();
        List<Node> actions = new ArrayList<>();
        for (Node k : n.kids) {
            switch (k.key()) {
                case "aliases" -> {
                    for (String a : k.value().split(","))
                        if (!a.isBlank()) aliases.add(a.trim().replace("/", "").toLowerCase());
                }
                case "description" -> desc = k.value();
                case "usage" -> usage = k.value();
                case "trigger" -> actions = k.kids;
                default -> {}
            }
        }
        map.register("minisk", new SkCommand(this, name, desc, usage, aliases, actions));
    }

    private void registerMenu(Node n) {
        String head = n.text.substring(5).trim();
        if (head.endsWith(":")) head = head.substring(0, head.length() - 1).trim();
        Menu m = new Menu();
        m.name = head.toLowerCase();
        for (Node k : n.kids) {
            String key = k.key();
            if (key.equals("title")) m.title = k.value();
            else if (key.equals("rows")) m.rows = Math.max(1, Math.min(6, Integer.parseInt(k.value().trim())));
            else if (key.equals("fill")) m.fill = k.value();
            else if (key.startsWith("slot")) {
                Menu.Item item = new Menu.Item();
                for (Node f : k.kids) {
                    switch (f.key()) {
                        case "item" -> item.material = f.value();
                        case "name" -> item.name = f.value();
                        case "amount" -> item.amount = Integer.parseInt(f.value().trim());
                        case "lore" -> {
                            for (String l : f.value().split("\\|")) item.lore.add(Util.unquote(l.trim()));
                        }
                        case "on click" -> item.actions.addAll(f.kids);
                        default -> {}
                    }
                }
                String spec = key.substring(key.indexOf(' ') + 1);
                for (String part : spec.split(",")) {
                    part = part.trim();
                    if (part.contains("-")) {
                        String[] r = part.split("-");
                        for (int i = Integer.parseInt(r[0].trim()); i <= Integer.parseInt(r[1].trim()); i++)
                            m.items.put(i, item);
                    } else if (!part.isEmpty()) {
                        m.items.put(Integer.parseInt(part), item);
                    }
                }
            }
        }
        menus.put(m.name, m);
    }

    public void open(String name, Player p) {
        Menu m = menus.get(name.toLowerCase());
        if (m == null) {
            p.sendMessage(Util.color("&cMenu not found: " + name));
            return;
        }
        Inventory inv = Bukkit.createInventory(new Menu.Holder(m), m.rows * 9, Util.color(fill(m.title, p, new String[0])));
        if (m.fill != null) {
            Material fm = Material.matchMaterial(m.fill);
            if (fm != null) {
                ItemStack f = new ItemStack(fm);
                ItemMeta meta = f.getItemMeta();
                meta.displayName(Component.text(" "));
                f.setItemMeta(meta);
                for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, f);
            }
        }
        for (Map.Entry<Integer, Menu.Item> e : m.items.entrySet()) {
            if (e.getKey() < 0 || e.getKey() >= inv.getSize()) continue;
            Menu.Item it = e.getValue();
            Material mat = Material.matchMaterial(it.material);
            if (mat == null) mat = Material.STONE;
            ItemStack stack = new ItemStack(mat, Math.max(1, it.amount));
            ItemMeta meta = stack.getItemMeta();
            if (it.name != null) meta.displayName(Util.noItalic(Util.color(fill(it.name, p, new String[0]))));
            if (!it.lore.isEmpty()) {
                List<Component> lore = new ArrayList<>();
                for (String l : it.lore) lore.add(Util.noItalic(Util.color(fill(l, p, new String[0]))));
                meta.lore(lore);
            }
            stack.setItemMeta(meta);
            inv.setItem(e.getKey(), stack);
        }
        p.openInventory(inv);
    }

    public String fill(String s, CommandSender cs, String[] a) {
        s = s.replace("%player%", cs.getName())
                .replace("%args%", String.join(" ", a))
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
        if (cs instanceof Player p) s = s.replace("%world%", p.getWorld().getName());
        for (int i = 0; i < a.length; i++) s = s.replace("%arg" + (i + 1) + "%", a[i]);
        return s.replaceAll("%arg\\d+%", "");
    }

    public void run(List<Node> actions, CommandSender s, String[] args) {
        for (Node n : actions) {
            try {
                exec(n, s, args);
            } catch (Exception e) {
                plugin.getLogger().warning("Script error in '" + n.text + "': " + e.getMessage());
            }
        }
    }

    private void exec(Node n, CommandSender s, String[] args) {
        String line = fill(n.text, s, args);
        List<String> t = Util.tokens(line);
        if (t.isEmpty()) return;
        String c = t.get(0).toLowerCase();
        String rest = line.length() > c.length() ? line.substring(c.length()).trim() : "";
        Player p = s instanceof Player pl ? pl : null;

        switch (c) {
            case "send", "message" -> s.sendMessage(Util.chat(Util.unquote(rest)));
            case "broadcast" -> Bukkit.broadcast(Util.chat(Util.unquote(rest)));
            case "link" -> {
                if (t.size() >= 3) s.sendMessage(Util.link(t.get(1), t.get(2)));
                else if (t.size() == 2) s.sendMessage(Util.link(t.get(1), t.get(1)));
            }
            case "title" -> {
                if (p != null) p.showTitle(Title.title(Util.color(t.size() > 1 ? t.get(1) : ""),
                        Util.color(t.size() > 2 ? t.get(2) : "")));
            }
            case "actionbar" -> {
                if (p != null) p.sendActionBar(Util.color(Util.unquote(rest)));
            }
            case "sound" -> {
                if (p != null && t.size() > 1)
                    p.playSound(p.getLocation(), t.get(1).toLowerCase().replace('_', '.'), 1f, 1f);
            }
            case "command" -> {
                if (p != null) p.performCommand(Util.unquote(rest).replaceFirst("^/", ""));
            }
            case "menu", "open" -> {
                if (p != null) {
                    String name = t.get(t.size() - 1);
                    Bukkit.getScheduler().runTask(plugin.host(), () -> open(name, p));
                }
            }
            case "close" -> {
                if (p != null) Bukkit.getScheduler().runTask(plugin.host(), () -> p.closeInventory());
            }
            case "give" -> {
                if (p != null && t.size() > 1) {
                    Material m = Material.matchMaterial(t.get(1));
                    int amt = t.size() > 2 ? Integer.parseInt(t.get(2)) : 1;
                    if (m != null) p.getInventory().addItem(new ItemStack(m, amt))
                            .values().forEach(i -> p.getWorld().dropItem(p.getLocation(), i));
                }
            }
            default -> plugin.getLogger().warning("Unknown action: " + c);
        }
    }
}
