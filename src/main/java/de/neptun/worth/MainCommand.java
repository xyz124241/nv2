package de.neptun.worth;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Behandelt /worth, /sell, /shop und /neptunworth. */
public final class MainCommand implements CommandExecutor, TabCompleter {

    private final NeptunWorth plugin;

    public MainCommand(NeptunWorth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "worth":
                return worth(s, a);
            case "sell":
                return sell(s, a);
            case "shop":
                return shop(s, a);
            case "neptunworth":
                return admin(s, a);
            default:
                return false;
        }
    }

    private Player player(CommandSender s, String permission) {
        if (!(s instanceof Player)) {
            s.sendMessage(plugin.msg("player-only"));
            return null;
        }
        if (!s.hasPermission(permission)) {
            s.sendMessage(plugin.msg("no-permission"));
            return null;
        }
        return (Player) s;
    }

    private Currency currency(Player p, String[] a, String usage) {
        if (a.length == 0) {
            p.sendMessage(plugin.msg("usage", "usage", usage));
            return null;
        }
        Currency c = Currency.parse(a[0]);
        if (c == null) {
            p.sendMessage(plugin.msg("unknown-currency"));
        }
        return c;
    }

    private String joinFrom(String[] a, int from) {
        return a.length > from ? String.join(" ", Arrays.copyOfRange(a, from, a.length)) : "";
    }

    private boolean worth(CommandSender s, String[] a) {
        Player p = player(s, "neptunworth.worth");
        if (p == null) {
            return true;
        }
        Currency c = currency(p, a, "/worth <money|neptuns> [hand|Suchbegriff]");
        if (c == null) {
            return true;
        }
        if (a.length >= 2 && a[1].equalsIgnoreCase("hand")) {
            ItemStack item = p.getInventory().getItemInMainHand();
            if (item.getType().isAir()) {
                p.sendMessage(plugin.msg("no-item"));
                return true;
            }
            for (String line : plugin.prices().breakdown(item, c)) {
                p.sendMessage(line);
            }
            return true;
        }
        plugin.browser().open(p, BrowserGui.Mode.WORTH, c, joinFrom(a, 1));
        return true;
    }

    private boolean shop(CommandSender s, String[] a) {
        Player p = player(s, "neptunworth.shop");
        if (p == null) {
            return true;
        }
        if (!plugin.getConfig().getBoolean("shop.enabled", true)) {
            p.sendMessage(plugin.msg("shop-disabled"));
            return true;
        }
        Currency c = currency(p, a, "/shop <money|neptuns> [Suchbegriff]");
        if (c == null) {
            return true;
        }
        plugin.browser().open(p, BrowserGui.Mode.SHOP, c, joinFrom(a, 1));
        return true;
    }

    private boolean sell(CommandSender s, String[] a) {
        Player p = player(s, "neptunworth.sell");
        if (p == null) {
            return true;
        }
        Currency c = currency(p, a, "/sell <money|neptuns> [hand|all]");
        if (c == null) {
            return true;
        }
        if (a.length >= 2) {
            if (a[1].equalsIgnoreCase("hand")) {
                plugin.trading().sell(p, c, p.getInventory(), new int[]{p.getInventory().getHeldItemSlot()},
                        it -> true, -1);
            } else if (a[1].equalsIgnoreCase("all")) {
                if (!p.hasPermission("neptunworth.sell.all")) {
                    p.sendMessage(plugin.msg("no-permission"));
                    return true;
                }
                plugin.trading().sell(p, c, p.getInventory(), Trading.range(0, 36), it -> true, -1);
            } else {
                p.sendMessage(plugin.msg("usage", "usage", "/sell <money|neptuns> [hand|all]"));
            }
            return true;
        }
        plugin.sellGui().open(p, c);
        return true;
    }

    private boolean admin(CommandSender s, String[] a) {
        if (!s.hasPermission("neptunworth.admin")) {
            s.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (a.length == 0) {
            s.sendMessage(plugin.msg("usage", "usage", "/neptunworth <reload|set <money|neptuns> <Preis>>"));
            return true;
        }
        if (a[0].equalsIgnoreCase("reload")) {
            plugin.reloadAll();
            s.sendMessage(plugin.msg("reload-ok"));
            return true;
        }
        if (a[0].equalsIgnoreCase("set")) {
            if (!(s instanceof Player)) {
                s.sendMessage(plugin.msg("player-only"));
                return true;
            }
            Player p = (Player) s;
            if (a.length < 3) {
                p.sendMessage(plugin.msg("usage", "usage", "/neptunworth set <money|neptuns> <Preis>"));
                return true;
            }
            Currency c = Currency.parse(a[1]);
            if (c == null) {
                p.sendMessage(plugin.msg("unknown-currency"));
                return true;
            }
            Material m = p.getInventory().getItemInMainHand().getType();
            if (m.isAir()) {
                p.sendMessage(plugin.msg("no-item"));
                return true;
            }
            double price;
            try {
                price = Double.parseDouble(a[2].replace(',', '.'));
            } catch (NumberFormatException ex) {
                p.sendMessage(plugin.msg("invalid-number"));
                return true;
            }
            if (price < 0 || Double.isNaN(price) || Double.isInfinite(price)) {
                p.sendMessage(plugin.msg("invalid-number"));
                return true;
            }
            plugin.prices().setPrice(m, c, price);
            p.sendMessage(plugin.msg("price-set", "item", m.name(), "price", plugin.fmt(c, price)));
            return true;
        }
        s.sendMessage(plugin.msg("usage", "usage", "/neptunworth <reload|set>"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> options = new ArrayList<>();
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        if (name.equals("neptunworth")) {
            if (a.length == 1) {
                options.addAll(Arrays.asList("reload", "set"));
            } else if (a.length == 2 && a[0].equalsIgnoreCase("set")) {
                options.addAll(Arrays.asList("money", "neptuns"));
            }
        } else if (a.length == 1) {
            options.addAll(Arrays.asList("money", "neptuns"));
        } else if (a.length == 2) {
            if (name.equals("worth")) {
                options.add("hand");
            } else if (name.equals("sell")) {
                options.addAll(Arrays.asList("hand", "all"));
            }
        }
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.startsWith(last)) {
                out.add(o);
            }
        }
        return out;
    }
}
