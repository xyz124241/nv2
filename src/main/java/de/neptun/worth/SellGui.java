package de.neptun.worth;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * /sell money|neptuns: Items hineinlegen, mit dem grünen Button verkaufen.
 * Beim Schließen bekommst du alles Nicht-Verkaufte zurück.
 */
public final class SellGui implements Listener {

    private static final int ITEM_SLOTS = 45;
    private static final int CONFIRM = 49;
    private static final int CANCEL = 53;
    private static final int[] SLOTS = Trading.range(0, ITEM_SLOTS);

    public static final class Holder implements InventoryHolder {
        final Currency currency;
        Inventory inv;

        Holder(Currency currency) {
            this.currency = currency;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final NeptunWorth plugin;

    public SellGui(NeptunWorth plugin) {
        this.plugin = plugin;
    }

    private ItemStack named(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.color(name));
            List<String> l = new ArrayList<>();
            for (String s : lore) {
                l.add(plugin.color(s));
            }
            meta.setLore(l);
            it.setItemMeta(meta);
        }
        return it;
    }

    public void open(Player p, Currency c) {
        if (!plugin.economy().isAvailable(c)) {
            p.sendMessage(plugin.msg("currency-unavailable", "currency", plugin.cname(c)));
            return;
        }
        Holder h = new Holder(c);
        h.inv = Bukkit.createInventory(h, 54, plugin.color("&8Verkaufen &7- &6" + plugin.cname(c)));
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int s = ITEM_SLOTS; s < 54; s++) {
            h.inv.setItem(s, filler);
        }
        h.inv.setItem(45, named(Material.BOOK, "&bSo geht's",
                "&7Lege Items in die obere Fläche.",
                "&7Klicke dann auf den grünen Button.",
                "&7Alles Nicht-Verkäufliche bekommst du zurück.",
                "&7Enchants, Tränke & Haltbarkeit zählen mit."));
        h.inv.setItem(CANCEL, named(Material.RED_STAINED_GLASS_PANE, "&cAbbrechen", "&7Schließt das Menü, du bekommst alles zurück."));
        updateTotal(h);
        p.openInventory(h.inv);
    }

    private void updateTotal(Holder h) {
        double total = 0;
        int count = 0;
        for (int s : SLOTS) {
            ItemStack it = h.inv.getItem(s);
            if (it == null || it.getType().isAir()) {
                continue;
            }
            double unit = plugin.prices().unitPrice(it, h.currency);
            if (unit > 0) {
                total += unit * it.getAmount();
                count += it.getAmount();
            }
        }
        double pay = plugin.payout(h.currency, total);
        h.inv.setItem(CONFIRM, named(Material.LIME_STAINED_GLASS_PANE, "&aVerkaufen",
                "&7Verkäufliche Items: &f" + count,
                "&7Erlös: &f" + plugin.fmt(h.currency, pay),
                "",
                "&eKlick zum Verkaufen"));
    }

    private void scheduleUpdate(Holder h) {
        Bukkit.getScheduler().runTask(plugin.host(), () -> updateTotal(h));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder)) {
            return;
        }
        Holder h = (Holder) top.getHolder();
        if (!(e.getWhoClicked() instanceof Player)) {
            return;
        }
        Player p = (Player) e.getWhoClicked();

        if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            e.setCancelled(true);
            return;
        }
        int raw = e.getRawSlot();
        if (raw >= ITEM_SLOTS && raw < top.getSize()) {
            e.setCancelled(true);
            if (raw == CONFIRM) {
                if (plugin.trading().sell(p, h.currency, h.inv, SLOTS, it -> true, -1)) {
                    updateTotal(h);
                    Bukkit.getScheduler().runTask(plugin.host(), () -> p.closeInventory());
                }
            } else if (raw == CANCEL) {
                Bukkit.getScheduler().runTask(plugin.host(), () -> p.closeInventory());
            }
            return;
        }
        scheduleUpdate(h);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder)) {
            return;
        }
        for (int raw : e.getRawSlots()) {
            if (raw >= ITEM_SLOTS && raw < top.getSize()) {
                e.setCancelled(true);
                return;
            }
        }
        scheduleUpdate((Holder) top.getHolder());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        Inventory inv = e.getInventory();
        if (!(inv.getHolder() instanceof Holder) || !(e.getPlayer() instanceof Player)) {
            return;
        }
        Player p = (Player) e.getPlayer();
        List<ItemStack> back = new ArrayList<>();
        for (int s : SLOTS) {
            ItemStack it = inv.getItem(s);
            if (it != null && !it.getType().isAir()) {
                back.add(it);
                inv.setItem(s, null);
            }
        }
        plugin.trading().giveItems(p, back);
    }
}
