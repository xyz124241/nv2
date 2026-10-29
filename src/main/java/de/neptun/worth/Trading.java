package de.neptun.worth;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Verkaufen und Kaufen. */
public final class Trading {

    private final NeptunWorth plugin;

    public Trading(NeptunWorth plugin) {
        this.plugin = plugin;
    }

    public static int[] range(int from, int toExclusive) {
        int[] r = new int[toExclusive - from];
        for (int i = 0; i < r.length; i++) {
            r[i] = from + i;
        }
        return r;
    }

    /** Item ohne Enchants, ohne eigenen Namen/Lore (wird beim Klick im /worth-Menü verkauft). */
    public static boolean isPlain(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return true;
        }
        if (meta.hasEnchants() || meta.hasDisplayName() || meta.hasLore()) {
            return false;
        }
        if (meta instanceof EnchantmentStorageMeta && ((EnchantmentStorageMeta) meta).hasStoredEnchants()) {
            return false;
        }
        return true;
    }

    /**
     * Verkauft Items aus den angegebenen Slots eines Inventars.
     *
     * @param max maximale Anzahl Items, -1 = alle
     * @return true, wenn etwas verkauft wurde
     */
    public boolean sell(Player p, Currency c, Inventory inv, int[] slots, Predicate<ItemStack> filter, int max) {
        if (!plugin.economy().isAvailable(c)) {
            p.sendMessage(plugin.msg("currency-unavailable", "currency", plugin.cname(c)));
            return false;
        }
        List<int[]> takes = new ArrayList<>();
        double total = 0;
        int count = 0;
        int remaining = max < 0 ? Integer.MAX_VALUE : max;
        for (int slot : slots) {
            if (remaining <= 0) {
                break;
            }
            ItemStack it = inv.getItem(slot);
            if (it == null || it.getType().isAir() || !filter.test(it)) {
                continue;
            }
            double unit = plugin.prices().unitPrice(it, c);
            if (unit <= 0) {
                continue;
            }
            int take = Math.min(it.getAmount(), remaining);
            takes.add(new int[]{slot, take});
            total += unit * take;
            count += take;
            remaining -= take;
        }
        if (takes.isEmpty()) {
            p.sendMessage(plugin.msg("nothing-sellable"));
            return false;
        }
        double pay = plugin.payout(c, total);
        if (pay <= 0) {
            p.sendMessage(plugin.msg("nothing-worth", "currency", plugin.cname(c)));
            return false;
        }

        List<ItemStack> removed = new ArrayList<>();
        for (int[] t : takes) {
            ItemStack it = inv.getItem(t[0]);
            ItemStack copy = it.clone();
            copy.setAmount(t[1]);
            removed.add(copy);
            if (t[1] >= it.getAmount()) {
                inv.setItem(t[0], null);
            } else {
                it.setAmount(it.getAmount() - t[1]);
                inv.setItem(t[0], it);
            }
        }
        if (!plugin.economy().deposit(p, c, pay)) {
            giveItems(p, removed);
            p.sendMessage(plugin.msg("error"));
            return false;
        }
        p.sendMessage(plugin.msg("sold", "count", String.valueOf(count), "amount", plugin.fmt(c, pay)));
        return true;
    }

    /** Verkauft normale Items eines Materials aus dem Spielerinventar (Klick im /worth-Menü). */
    public void sellMaterial(Player p, Currency c, Material m, int amount) {
        if (plugin.prices().base(m, c) <= 0) {
            p.sendMessage(plugin.msg("nothing-sellable"));
            return;
        }
        sell(p, c, p.getInventory(), range(0, 36), it -> it.getType() == m && isPlain(it), amount);
    }

    public void buy(Player p, Currency c, Material m, int amount) {
        double unit = plugin.prices().buyUnit(m, c);
        if (unit <= 0) {
            p.sendMessage(plugin.msg("not-buyable"));
            return;
        }
        if (!plugin.economy().isAvailable(c)) {
            p.sendMessage(plugin.msg("currency-unavailable", "currency", plugin.cname(c)));
            return;
        }
        double cost = c == Currency.MONEY
                ? Math.round(unit * amount * 100.0) / 100.0
                : Math.max(1, Math.ceil(unit * amount - 1e-9));
        if (!plugin.economy().has(p, c, cost)) {
            p.sendMessage(plugin.msg("not-enough", "price", plugin.fmt(c, cost)));
            return;
        }
        if (!plugin.economy().withdraw(p, c, cost)) {
            p.sendMessage(plugin.msg("error"));
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        int left = amount;
        int max = Math.max(1, m.getMaxStackSize());
        while (left > 0) {
            int n = Math.min(max, left);
            items.add(new ItemStack(m, n));
            left -= n;
        }
        giveItems(p, items);
        p.sendMessage(plugin.msg("bought", "count", String.valueOf(amount), "item", m.name(),
                "price", plugin.fmt(c, cost)));
    }

    /** Gibt Items zurück; was nicht ins Inventar passt, landet auf dem Boden. */
    public void giveItems(Player p, Collection<ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }
        Map<Integer, ItemStack> left = p.getInventory().addItem(items.toArray(new ItemStack[0]));
        if (!left.isEmpty()) {
            for (ItemStack i : left.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), i);
            }
            p.sendMessage(plugin.msg("inventory-full"));
        }
    }
}
