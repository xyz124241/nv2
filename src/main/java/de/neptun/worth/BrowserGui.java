package de.neptun.worth;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Das große Menü für /worth und /shop: alle Items, viele Seiten, Suche, Sortierung, Kategorien.
 */
public final class BrowserGui implements Listener {

    public enum Mode { WORTH, SHOP }

    private static final int PAGE_SIZE = 45;

    public static final class Holder implements InventoryHolder {
        final Mode mode;
        Currency currency;
        String search = "";
        Catalog.Sort sort = Catalog.Sort.NAME_ASC;
        Catalog.Category category = Catalog.Category.ALL;
        int page = 0;
        List<Material> view = new ArrayList<>();
        Inventory inv;

        Holder(Mode mode, Currency currency) {
            this.mode = mode;
            this.currency = currency;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final NeptunWorth plugin;
    private final Map<UUID, Holder> awaitingSearch = new java.util.concurrent.ConcurrentHashMap<>();

    public BrowserGui(NeptunWorth plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ Öffnen

    public void open(Player p, Mode mode, Currency currency, String search) {
        Holder h = new Holder(mode, currency);
        h.search = search == null ? "" : search;
        show(p, h);
    }

    private void show(Player p, Holder h) {
        h.inv = Bukkit.createInventory(h, 54, title(h));
        render(h);
        p.openInventory(h.inv);
    }

    private String title(Holder h) {
        String kind = h.mode == Mode.WORTH ? "Worth" : "Shop";
        return plugin.color("&8" + kind + " &7- &6" + plugin.cname(h.currency));
    }

    // ------------------------------------------------------------------ Liste berechnen

    private double price(Holder h, Material m) {
        return h.mode == Mode.SHOP ? plugin.prices().buyUnit(m, h.currency) : plugin.prices().base(m, h.currency);
    }

    private List<Material> compute(Holder h) {
        List<Material> out = new ArrayList<>();
        String[] words = h.search.toLowerCase(Locale.ROOT).trim().split("\\s+");
        boolean hasSearch = !h.search.isBlank();
        outer:
        for (Material m : Catalog.all()) {
            if (h.mode == Mode.SHOP && price(h, m) <= 0) {
                continue;
            }
            if (h.category != Catalog.Category.ALL && Catalog.categoryOf(m) != h.category) {
                continue;
            }
            if (hasSearch) {
                String text = Catalog.searchText(m);
                for (String w : words) {
                    if (!text.contains(w)) {
                        continue outer;
                    }
                }
            }
            out.add(m);
        }
        Comparator<Material> byName = Comparator.<Material, String>comparing(Catalog::germanName)
                .thenComparing(Material::name);
        Comparator<Material> cmp;
        switch (h.sort) {
            case NAME_DESC:
                cmp = byName.reversed();
                break;
            case PRICE_ASC:
                cmp = Comparator.<Material>comparingDouble(m -> plugin.prices().base(m, h.currency)).thenComparing(byName);
                break;
            case PRICE_DESC:
                cmp = Comparator.<Material>comparingDouble(m -> plugin.prices().base(m, h.currency)).reversed()
                        .thenComparing(byName);
                break;
            case COLOR:
                cmp = Comparator.<Material>comparingInt(Catalog::colorIndex).thenComparing(byName);
                break;
            case CATEGORY:
                cmp = Comparator.<Material>comparingInt(m -> Catalog.categoryOf(m).ordinal()).thenComparing(byName);
                break;
            case STACK:
                cmp = Comparator.<Material>comparingInt(m -> -m.getMaxStackSize()).thenComparing(byName);
                break;
            case NAME_ASC:
            default:
                cmp = byName;
                break;
        }
        out.sort(cmp);
        return out;
    }

    // ------------------------------------------------------------------ Darstellung

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
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack icon(Material m, Holder h) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta == null) {
            return it;
        }
        List<String> lore = new ArrayList<>();
        lore.add("");
        Currency cur = h.currency;
        double unit = price(h, m);
        if (h.mode == Mode.WORTH) {
            double mp = plugin.prices().base(m, Currency.MONEY);
            double np = plugin.prices().base(m, Currency.NEPTUNS);
            if (mp <= 0 && np <= 0) {
                lore.add(plugin.color("&cNicht verkäuflich"));
            } else {
                lore.add(plugin.color((cur == Currency.MONEY ? "&a▶ " : "&7  ") + plugin.cname(Currency.MONEY)
                        + ": &f" + plugin.num(mp)));
                lore.add(plugin.color((cur == Currency.NEPTUNS ? "&a▶ " : "&7  ") + plugin.cname(Currency.NEPTUNS)
                        + ": &f" + plugin.num(np)));
                if (m.getMaxStackSize() > 1 && unit > 0) {
                    lore.add(plugin.color("&7Stapel (" + m.getMaxStackSize() + "): &f"
                            + plugin.num(unit * m.getMaxStackSize()) + " " + plugin.cname(cur)));
                }
                if (unit > 0 && plugin.getConfig().getBoolean("worth.click-to-sell", true)) {
                    lore.add("");
                    lore.add(plugin.color("&eLinksklick: &f1 verkaufen"));
                    lore.add(plugin.color("&eRechtsklick: &f8 verkaufen"));
                    lore.add(plugin.color("&eShift+Links: &f64 verkaufen"));
                    lore.add(plugin.color("&eShift+Rechts: &falle verkaufen"));
                }
            }
        } else {
            lore.add(plugin.color("&7Preis: &f" + plugin.num(unit) + " " + plugin.cname(cur)));
            lore.add("");
            lore.add(plugin.color("&eLinksklick: &f1 kaufen"));
            lore.add(plugin.color("&eRechtsklick: &f8 kaufen"));
            lore.add(plugin.color("&eShift+Links: &f64 kaufen"));
            lore.add(plugin.color("&eShift+Rechts: &f16 kaufen"));
        }
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return it;
    }

    private void render(Holder h) {
        h.view = compute(h);
        int pages = Math.max(1, (int) Math.ceil(h.view.size() / (double) PAGE_SIZE));
        if (h.page >= pages) {
            h.page = pages - 1;
        }
        if (h.page < 0) {
            h.page = 0;
        }
        Inventory inv = h.inv;
        inv.clear();
        for (int s = 0; s < PAGE_SIZE; s++) {
            int idx = h.page * PAGE_SIZE + s;
            if (idx >= h.view.size()) {
                break;
            }
            inv.setItem(s, icon(h.view.get(idx), h));
        }
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int s = PAGE_SIZE; s < 54; s++) {
            inv.setItem(s, filler);
        }

        inv.setItem(45, h.page > 0
                ? named(Material.ARROW, "&e« Vorherige Seite", "&7Seite " + h.page + " von " + pages)
                : filler);

        List<String> sortLore = new ArrayList<>();
        sortLore.add("&7Sortierung ändern:");
        for (Catalog.Sort s : Catalog.Sort.values()) {
            sortLore.add((s == h.sort ? "&a▶ " : "&8  ") + s.label);
        }
        sortLore.add("");
        sortLore.add("&eLinksklick: &fnächste");
        sortLore.add("&eRechtsklick: &fvorherige");
        inv.setItem(46, named(Material.HOPPER, "&bSortieren", sortLore.toArray(new String[0])));

        inv.setItem(47, named(Material.OAK_SIGN, "&bSuchen",
                "&7Aktuell: &f" + (h.search.isBlank() ? "-" : h.search),
                "",
                "&eKlick: &fSuchbegriff im Chat eingeben",
                "&7Englisch oder Deutsch, z.B. &fdiamant spitzhacke"));

        List<String> catLore = new ArrayList<>();
        catLore.add("&7Kategorie ändern:");
        for (Catalog.Category c : Catalog.Category.values()) {
            catLore.add((c == h.category ? "&a▶ " : "&8  ") + c.label);
        }
        catLore.add("");
        catLore.add("&eLinksklick: &fnächste");
        catLore.add("&eRechtsklick: &fvorherige");
        inv.setItem(48, named(h.category.icon, "&bKategorie", catLore.toArray(new String[0])));

        inv.setItem(49, named(Material.BOOK, "&6" + (h.mode == Mode.WORTH ? "Worth" : "Shop") + " - "
                        + plugin.cname(h.currency),
                "&7Seite: &f" + (h.page + 1) + "/" + pages,
                "&7Items: &f" + h.view.size(),
                "&7Suche: &f" + (h.search.isBlank() ? "-" : h.search),
                "&7Kategorie: &f" + h.category.label,
                "&7Sortierung: &f" + h.sort.label));

        inv.setItem(50, named(Material.BARRIER, "&cFilter zurücksetzen", "&7Löscht Suche und Kategorie."));

        Currency other = h.currency.other();
        inv.setItem(51, named(h.currency == Currency.MONEY ? Material.NETHER_STAR : Material.EMERALD,
                "&aWechsel zu " + plugin.cname(other), "&7Gleiche Ansicht in " + plugin.cname(other) + "."));

        inv.setItem(53, h.page < pages - 1
                ? named(Material.ARROW, "&eNächste Seite »", "&7Seite " + (h.page + 2) + " von " + pages)
                : filler);
    }

    // ------------------------------------------------------------------ Events

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder)) {
            return;
        }
        e.setCancelled(true);
        Holder h = (Holder) top.getHolder();
        if (e.getClickedInventory() == null || e.getClickedInventory() != top) {
            return;
        }
        if (!(e.getWhoClicked() instanceof Player)) {
            return;
        }
        Player p = (Player) e.getWhoClicked();
        int slot = e.getRawSlot();
        ClickType click = e.getClick();

        if (slot < PAGE_SIZE) {
            int idx = h.page * PAGE_SIZE + slot;
            if (idx < h.view.size()) {
                itemClick(p, h, h.view.get(idx), click);
            }
            return;
        }
        boolean right = click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT;
        switch (slot) {
            case 45:
                h.page--;
                render(h);
                break;
            case 53:
                h.page++;
                render(h);
                break;
            case 46: {
                Catalog.Sort[] all = Catalog.Sort.values();
                h.sort = all[Math.floorMod(h.sort.ordinal() + (right ? -1 : 1), all.length)];
                h.page = 0;
                render(h);
                break;
            }
            case 48: {
                Catalog.Category[] all = Catalog.Category.values();
                h.category = all[Math.floorMod(h.category.ordinal() + (right ? -1 : 1), all.length)];
                h.page = 0;
                render(h);
                break;
            }
            case 47:
                awaitingSearch.put(p.getUniqueId(), h);
                p.sendMessage(plugin.msg("search-prompt"));
                Bukkit.getScheduler().runTask(plugin.host(), () -> p.closeInventory());
                break;
            case 50:
                h.search = "";
                h.category = Catalog.Category.ALL;
                h.page = 0;
                render(h);
                break;
            case 51: {
                Holder n = new Holder(h.mode, h.currency.other());
                n.search = h.search;
                n.sort = h.sort;
                n.category = h.category;
                Bukkit.getScheduler().runTask(plugin.host(), () -> show(p, n));
                break;
            }
            default:
                break;
        }
    }

    private void itemClick(Player p, Holder h, Material m, ClickType click) {
        if (h.mode == Mode.WORTH) {
            if (!plugin.getConfig().getBoolean("worth.click-to-sell", true)) {
                return;
            }
            int amount;
            switch (click) {
                case LEFT:
                    amount = 1;
                    break;
                case RIGHT:
                    amount = 8;
                    break;
                case SHIFT_LEFT:
                    amount = 64;
                    break;
                case SHIFT_RIGHT:
                    amount = -1;
                    break;
                default:
                    return;
            }
            plugin.trading().sellMaterial(p, h.currency, m, amount);
        } else {
            int amount;
            switch (click) {
                case LEFT:
                    amount = 1;
                    break;
                case RIGHT:
                    amount = 8;
                    break;
                case SHIFT_LEFT:
                    amount = 64;
                    break;
                case SHIFT_RIGHT:
                    amount = 16;
                    break;
                default:
                    return;
            }
            plugin.trading().buy(p, h.currency, m, amount);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Holder h = awaitingSearch.remove(e.getPlayer().getUniqueId());
        if (h == null) {
            return;
        }
        e.setCancelled(true);
        String text = e.getMessage().trim();
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin.host(), () -> {
            if (text.equalsIgnoreCase("abbrechen") || text.equalsIgnoreCase("cancel")) {
                p.sendMessage(plugin.msg("search-cancelled"));
            } else {
                h.search = text.equals("-") ? "" : text;
                h.page = 0;
            }
            render(h);
            p.openInventory(h.inv);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        awaitingSearch.remove(e.getPlayer().getUniqueId());
    }
}
