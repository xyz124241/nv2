package de.neptun.ah;

import de.neptun.core.Module;
import de.neptun.core.NeptunCore;
import de.neptun.worth.Currency;
import de.neptun.worth.EconomyBridge;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class NeptunAH extends Module implements Listener {
    private final EconomyBridge eco = new EconomyBridge();
    public NeptunAH(NeptunCore host) { super(host, "ah"); }
    @Override public String[] commands() { return new String[]{"ah"}; }
    @Override public String legacyFolder() { return "NeptunAH"; }
    private final Map<Integer, Listing> listings = new LinkedHashMap<>();
    private final Map<UUID, Integer> page = new HashMap<>();
    private final Map<UUID, String> search = new HashMap<>();
    private final Set<UUID> awaitingSearch = ConcurrentHashMap.newKeySet();
    private final NamespacedKey listingKey = new NamespacedKey(host(), "listing_id");
    private File listingsFile, historyFile;
    private YamlConfiguration history;

    @Override public void onEnable() {
        saveDefaultConfig();
        listingsFile = new File(getDataFolder(), "listings.yml");
        historyFile = new File(getDataFolder(), "history.yml");
        loadListings();
        history = YamlConfiguration.loadConfiguration(historyFile);
        Objects.requireNonNull(getCommand("ah")).setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, host());
        getLogger().info("[ah] Auktionshaus aktiv. Vault: " + eco.isAvailable(Currency.MONEY) + ", PlayerPoints: " + eco.isAvailable(Currency.NEPTUNS));
    }

    private String c(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
    private String msg(String s) { return c(getConfig().getString("prefix", "&8[&f&lNeptun AH&8] &f") + s); }
    private void say(CommandSender s, String t) { s.sendMessage(msg(t)); }


    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { say(sender, "&cNur Spieler können das nutzen."); return true; }
        if (args.length == 0) { page.put(p.getUniqueId(), 0); open(p); return true; }
        if (args[0].equalsIgnoreCase("history")) { openHistory(p, 0); return true; }
        if (args[0].equalsIgnoreCase("sell")) {
            if (args.length < 3) { say(p, "&fVerwendung: &8/ah sell <preis> <money|neptuns>"); return true; }
            double price;
            try { price = Double.parseDouble(args[1]); } catch (NumberFormatException ex) { say(p, "&cUngültiger Preis."); return true; }
            if (!Double.isFinite(price) || price <= 0) { say(p, "&cDer Preis muss größer als 0 sein."); return true; }
            String currency = args[2].toLowerCase(Locale.ROOT);
            if (!currency.equals("money") && !currency.equals("neptuns") && !currency.equals("points")) {
                say(p, "&cWährung: money oder neptuns."); return true;
            }
            if (currency.equals("points")) currency = "neptuns";
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) { say(p, "&cHalte das Item in der Hand, das du verkaufen möchtest."); return true; }
            int limit = limit(p);
            long active = listings.values().stream().filter(l -> l.seller.equals(p.getUniqueId())).count();
            if (active >= limit) { say(p, getConfig().getString("messages.list-full", "&cLimit erreicht.").replace("%limit", String.valueOf(limit))); return true; }
            if (hand.getType().name().endsWith("SHULKER_BOX") && !hasShulkerContents(hand)) {
                // Empty shulkers are allowed too.
            }
            ItemStack listed = hand.clone();
            p.getInventory().setItemInMainHand(null);
            int id = listings.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
            listings.put(id, new Listing(id, p.getUniqueId(), p.getName(), listed, price, currency, System.currentTimeMillis()));
            saveListings();
            record(p.getUniqueId(), p.getName(), p.getUniqueId(), p.getName(), "LISTED", listed, price, currency, id);
            say(p, "&fAngebot erstellt: &8&l" + listed.getAmount() + "x " + pretty(listed) + " &ffür &8&l" + fmt(price) + " " + currency + "&f.");
            return true;
        }
        say(p, "&f/ah &8| &f/ah sell <preis> <money|neptuns> &8| &f/ah history");
        return true;
    }

    private int limit(Player p) {
        String[] ranks = {"neptun","lunar","astro","astat","titan","platin","member"};
        for (String r : ranks) if (p.hasPermission("neptunah.limit." + r)) return getConfig().getInt("limits." + r, 3);
        return 3;
    }
    private void open(Player p) {
        int pg = Math.max(0, page.getOrDefault(p.getUniqueId(), 0));
        int size = 54;
        Inventory inv = Bukkit.createInventory(null, size, c(getConfig().getString("menu.title", "&8&lNeptun AH")) + " &8(" + (pg + 1) + ")");
        String query = search.getOrDefault(p.getUniqueId(), "");
        List<Listing> visible = listings.values().stream()
            .filter(l -> query.isBlank() || pretty(l.item).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))
                    || l.item.getType().name().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
            .sorted(Comparator.comparingLong(l -> -l.created)).toList();
        int start = pg * 45;
        for (int i = start; i < Math.min(visible.size(), start + 45); i++) {
            Listing l = visible.get(i);
            ItemStack icon = l.item.clone();
            ItemMeta meta = icon.getItemMeta();
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(c("&8&m----------------"));
            lore.add(c("&fPreis: &8&l" + fmt(l.price) + " " + l.currency));
            lore.add(c("&fVerkäufer: &8" + l.sellerName));
            lore.add(c("&7Klicken zum Kaufen"));
            if (isShulker(icon)) lore.add(c("&eRechtsklick: Inhalt ansehen"));
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(listingKey, PersistentDataType.INTEGER, l.id);
            icon.setItemMeta(meta);
            inv.setItem(i - start, icon);
        }
        inv.setItem(45, named(Material.COMPASS, "&f&lSuchen", "&8Klicken und Suchbegriff eingeben", "&8Aktuell: " + (query.isBlank() ? "alle Items" : query)));
        inv.setItem(48, named(Material.ARROW, "&f&lVorherige Seite"));
        inv.setItem(49, named(Material.WRITABLE_BOOK, "&f&lAH-History", "&8Deine Käufe und Verkäufe"));
        inv.setItem(50, named(Material.ARROW, "&f&lNächste Seite"));
        inv.setItem(53, named(Material.BARRIER, "&8&lSchließen"));
        p.openInventory(inv);
    }
    private void openHistory(Player p, int pg) {
        Inventory inv = Bukkit.createInventory(null, 54, c("&8&lAH-History") + " &8(" + (pg+1) + ")");
        List<String> keys = new ArrayList<>(history.getKeys(false));
        Collections.reverse(keys);
        int start = pg * 45, slot = 0;
        for (int i=start; i<Math.min(keys.size(), start+45); i++) {
            String k=keys.get(i), base=k+".";
            UUID buyerId=UUID.fromString(history.getString(base+"buyerUuid", p.getUniqueId().toString()));
            UUID sellerId=UUID.fromString(history.getString(base+"sellerUuid", p.getUniqueId().toString()));
            if (!buyerId.equals(p.getUniqueId()) && !sellerId.equals(p.getUniqueId())) continue;
            ItemStack it=history.getItemStack(base+"item");
            if (it==null) it=new ItemStack(Material.PAPER);
            ItemMeta m=it.getItemMeta();
            List<String> lore=m.hasLore()?new ArrayList<>(m.getLore()):new ArrayList<>();
            lore.add(c("&8Aktion: &f"+history.getString(base+"action","UNKNOWN")));
            lore.add(c("&8Preis: &f"+fmt(history.getDouble(base+"price"))+" "+history.getString(base+"currency","money")));
            lore.add(c("&8Von: &f"+history.getString(base+"sellerName","?")));
            lore.add(c("&8An: &f"+history.getString(base+"buyerName","?")));
            lore.add(c("&8Zeit: &f"+new Date(history.getLong(base+"time"))));
            m.setLore(lore); it.setItemMeta(m); inv.setItem(slot++,it);
            if(slot>=45) break;
        }
        inv.setItem(48,named(Material.ARROW,"&f&lVorherige Seite"));
        inv.setItem(50,named(Material.ARROW,"&f&lNächste Seite"));
        inv.setItem(53,named(Material.BARRIER,"&8&lSchließen"));
        p.openInventory(inv);
    }
    private ItemStack named(Material material, String title, String... lore) {
        ItemStack i=new ItemStack(material); ItemMeta m=i.getItemMeta(); m.setDisplayName(c(title));
        if(lore.length>0)m.setLore(Arrays.stream(lore).map(this::c).toList()); i.setItemMeta(m); return i;
    }

    @EventHandler public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title=e.getView().getTitle();
        if (title.startsWith(c(getConfig().getString("menu.title","&8&lNeptun AH"))) || title.startsWith(c("&8&lAH-History"))) {
            e.setCancelled(true);
            if (e.getClickedInventory()==null || e.getClickedInventory()!=e.getView().getTopInventory()) return;
            ItemStack item=e.getCurrentItem(); if(item==null || item.getType().isAir()) return;
            int slot=e.getRawSlot();
            if (title.startsWith(c("&8&lAH-History"))) {
                if(slot==53) p.closeInventory();
                else if(slot==48) openHistory(p,Math.max(0,page.getOrDefault(p.getUniqueId(),0)-1));
                else if(slot==50) openHistory(p,page.getOrDefault(p.getUniqueId(),0)+1);
                return;
            }
            if(slot==45) { p.closeInventory(); awaitingSearch.add(p.getUniqueId()); p.sendMessage(msg("&fSchreibe deinen Suchbegriff in den Chat. Schreibe &8abbrechen &fum abzubrechen.")); return; }
            if(slot==48) { page.put(p.getUniqueId(),Math.max(0,page.getOrDefault(p.getUniqueId(),0)-1)); open(p); return; }
            if(slot==49) { openHistory(p,0); return; }
            if(slot==50) { page.put(p.getUniqueId(),page.getOrDefault(p.getUniqueId(),0)+1); open(p); return; }
            if(slot==53) { p.closeInventory(); return; }
            if(!item.hasItemMeta()) return;
            Integer id=item.getItemMeta().getPersistentDataContainer().get(listingKey,PersistentDataType.INTEGER);
            if(id==null) return;
            Listing l=listings.get(id); if(l==null) { say(p,"&cDieses Angebot ist nicht mehr verfügbar."); open(p); return; }
            if(isShulker(l.item) && e.isRightClick()) { openShulker(p,l.item); return; }
            buy(p,l);
        }
    }

    private void buy(Player buyer, Listing l) {
        if (buyer.getUniqueId().equals(l.seller)) { say(buyer,"&cDu kannst dein eigenes Angebot nicht kaufen."); return; }
        Currency cur = Currency.parse(l.currency);
        if (cur == null) { say(buyer, "&cUnbekannte Währung."); return; }
        double cost = cur == Currency.MONEY ? l.price : Math.ceil(l.price);
        if (!eco.isAvailable(cur)) { say(buyer, cur == Currency.MONEY ? "&cVault/Economy ist nicht verfügbar." : "&cPlayerPoints ist nicht verfügbar."); return; }
        if (!eco.has(buyer, cur, cost)) {
            say(buyer, getConfig().getString(cur == Currency.MONEY ? "messages.no-money" : "messages.no-neptuns", "&cZu wenig Guthaben."));
            return;
        }
        boolean paid = eco.withdraw(buyer, cur, cost);
        if(!paid) { say(buyer,"&cZahlung fehlgeschlagen."); return; }
        // Re-check active listing and deliver item.
        if(!listings.containsKey(l.id)) { refund(buyer,l); say(buyer,"&cAngebot nicht mehr verfügbar; Erstattung versucht."); return; }
        Map<Integer,ItemStack> overflow=buyer.getInventory().addItem(l.item.clone());
        overflow.values().forEach(it -> buyer.getWorld().dropItemNaturally(buyer.getLocation(),it));
        listings.remove(l.id); saveListings();
        if(l.currency.equals("money")) eco.deposit(Bukkit.getOfflinePlayer(l.seller), Currency.MONEY, l.price);
        else eco.deposit(Bukkit.getOfflinePlayer(l.seller), Currency.NEPTUNS, Math.floor(l.price));
        record(buyer.getUniqueId(),buyer.getName(),l.seller,l.sellerName,"BOUGHT",l.item,l.price,l.currency,l.id);
        say(buyer,"&fGekauft für &8&l"+fmt(l.price)+" "+l.currency+"&f.");
        Player seller=Bukkit.getPlayer(l.seller);
        if(seller!=null) say(seller,"&fDein Angebot wurde von &8&l"+buyer.getName()+" &fgekauft.");
        buyer.closeInventory();
    }
    private void refund(Player p, Listing l) {
        if(l.currency.equals("money")) eco.deposit(p, Currency.MONEY, l.price);
        else eco.deposit(p, Currency.NEPTUNS, Math.ceil(l.price));
    }
    private boolean isShulker(ItemStack i) { return i.getType().name().endsWith("SHULKER_BOX"); }
    private boolean hasShulkerContents(ItemStack i) {
        if(!(i.getItemMeta() instanceof BlockStateMeta meta))return false;
        return meta.getBlockState() instanceof ShulkerBox box && Arrays.stream(box.getInventory().getContents()).anyMatch(x->x!=null&&!x.getType().isAir());
    }
    private void openShulker(Player p, ItemStack item) {
        if(!(item.getItemMeta() instanceof BlockStateMeta meta) || !(meta.getBlockState() instanceof ShulkerBox box)) { say(p,"&cInhalt kann nicht angezeigt werden."); return; }
        Inventory inv=Bukkit.createInventory(null,27,c("&8&lShulker-Inhalt"));
        ItemStack[] contents=box.getInventory().getContents();
        for(int i=0;i<Math.min(27,contents.length);i++) if(contents[i]!=null) inv.setItem(i,contents[i].clone());
        p.openInventory(inv);
    }
    @EventHandler public void onChat(AsyncPlayerChatEvent e) {
        if(!awaitingSearch.remove(e.getPlayer().getUniqueId())) return;
        e.setCancelled(true);
        String text=e.getMessage().trim();
        Bukkit.getScheduler().runTask(host(),()->{
            if(text.equalsIgnoreCase("abbrechen")) { say(e.getPlayer(),"&cSuche abgebrochen."); return; }
            search.put(e.getPlayer().getUniqueId(),text); page.put(e.getPlayer().getUniqueId(),0);
            say(e.getPlayer(),"&fSuche: &8&l"+text); open(e.getPlayer());
        });
    }

    private void record(UUID buyer, String buyerName, UUID seller, String sellerName, String action, ItemStack item, double price, String currency, int id) {
        String key=String.valueOf(System.currentTimeMillis())+"-"+UUID.randomUUID();
        String b=key+".";
        history.set(b+"buyerUuid",buyer.toString()); history.set(b+"buyerName",buyerName);
        history.set(b+"sellerUuid",seller.toString()); history.set(b+"sellerName",sellerName);
        history.set(b+"action",action); history.set(b+"item",item); history.set(b+"price",price);
        history.set(b+"currency",currency); history.set(b+"listingId",id); history.set(b+"time",System.currentTimeMillis());
        try { history.save(historyFile); } catch(IOException ex) { getLogger().warning("History save failed: "+ex.getMessage()); }
    }
    private String pretty(ItemStack i) {
        ItemMeta m=i.getItemMeta();
        if(m!=null&&m.hasDisplayName()) return ChatColor.stripColor(m.getDisplayName());
        String n=i.getType().name().toLowerCase(Locale.ROOT).replace('_',' ');
        return Arrays.stream(n.split(" ")).map(s->s.isEmpty()?s:Character.toUpperCase(s.charAt(0))+s.substring(1)).reduce((a,b)->a+" "+b).orElse(n);
    }
    private String fmt(double d) { return String.format(Locale.US,"%.2f",d).replaceAll("\\.?0+$",""); }
    private void loadListings() {
        YamlConfiguration cfg=YamlConfiguration.loadConfiguration(listingsFile);
        for(String key:cfg.getKeys(false)) {
            ConfigurationSection s=cfg.getConfigurationSection(key); if(s==null)continue;
            try {
                int id=Integer.parseInt(key); UUID seller=UUID.fromString(s.getString("seller"));
                ItemStack item=s.getItemStack("item"); if(item==null)continue;
                listings.put(id,new Listing(id,seller,s.getString("seller-name","Unknown"),item,s.getDouble("price"),s.getString("currency","money"),s.getLong("created")));
            } catch(Exception ex) { getLogger().warning("Skipping invalid listing "+key); }
        }
    }
    private void saveListings() {
        YamlConfiguration cfg=new YamlConfiguration();
        for(Listing l:listings.values()) {
            String b=String.valueOf(l.id)+".";
            cfg.set(b+"seller",l.seller.toString()); cfg.set(b+"seller-name",l.sellerName);
            cfg.set(b+"item",l.item); cfg.set(b+"price",l.price); cfg.set(b+"currency",l.currency); cfg.set(b+"created",l.created);
        }
        try { cfg.save(listingsFile); } catch(IOException e) { getLogger().severe("Could not save listings: "+e.getMessage()); }
    }
    private record Listing(int id, UUID seller, String sellerName, ItemStack item, double price, String currency, long created) {}
}
