package de.neptun.trade;

import de.neptun.core.Module;
import de.neptun.core.NeptunCore;
import de.neptun.worth.Currency;
import de.neptun.worth.EconomyBridge;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.block.ShulkerBox;

import java.util.*;

public final class NeptunTrade extends Module implements Listener, TabCompleter {
    private final EconomyBridge eco = new EconomyBridge();
    public NeptunTrade(NeptunCore host) { super(host, "trade"); }
    @Override public String[] commands() { return new String[]{"trade"}; }
    @Override public String legacyFolder() { return "NeptunTrade"; }
    @Override public void onReload() { reloadConfig(); prefix = color(getConfig().getString("messages.prefix", "&8[&fNeptun&8] ")); }
    private final Map<UUID, UUID> requests = new HashMap<>();
    private final Map<UUID, Trade> trades = new HashMap<>();
    private final Map<UUID, String> inputMode = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRequest = new HashMap<>();
    private final Set<UUID> previewing = new HashSet<>();
    private String prefix;

    @Override public void onEnable() {
        saveDefaultConfig();
        prefix = color(getConfig().getString("messages.prefix", "&8[&fNeptun&8] "));
        getServer().getPluginManager().registerEvents(this, host());
        getLogger().info("[trade] Trade aktiv. Vault: " + eco.isAvailable(Currency.MONEY) + ", PlayerPoints: " + eco.isAvailable(Currency.NEPTUNS));
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        if (!p.hasPermission("neptuntrade.use")) { msg(p, "&8You do not have permission."); return true; }
        if (args.length == 0) { msg(p, "&fUse &8/trade <player> &for sneak and right-click a player. &8/trade accept &fto accept."); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "accept" -> accept(p);
            case "deny" -> { requests.remove(p.getUniqueId()); msg(p, "&8Trade request denied."); }
            case "cancel" -> cancelFor(p, "&8Trade cancelled.");
            case "money" -> { if (trades.containsKey(p.getUniqueId())) { inputMode.put(p.getUniqueId(), "money"); msg(p, getConfig().getString("messages.enter-amount")); } else msg(p, "&8You are not in a trade."); }
            case "neptuns", "points" -> { if (trades.containsKey(p.getUniqueId())) { inputMode.put(p.getUniqueId(), "points"); msg(p, getConfig().getString("messages.enter-amount")); } else msg(p, "&8You are not in a trade."); }
            default -> {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) { msg(p, "&fUsage: &8/trade <player>&f, &8/trade accept&f, &8/trade deny&f, &8/trade cancel&f, &8/trade money&f, &8/trade neptuns"); return true; }
                sendRequest(p, target);
            }
        }
        return true;
    }

    @EventHandler public void onInteract(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Player target) || !e.getPlayer().isSneaking()) return;
        e.setCancelled(true);
        sendRequest(e.getPlayer(), target);
    }

    /** Gemeinsame Anfrage-Logik fuer Shift+Rechtsklick und /trade <Spieler>. */
    private void sendRequest(Player sender, Player target) {
        if (sender.equals(target)) { msg(sender, "&8You cannot trade with yourself."); return; }
        if (!sender.hasPermission("neptuntrade.use")) { msg(sender, "&8You do not have permission."); return; }
        if (trades.containsKey(sender.getUniqueId()) || trades.containsKey(target.getUniqueId())) { msg(sender, getConfig().getString("messages.busy")); return; }
        long now = System.currentTimeMillis();
        if (now - lastRequest.getOrDefault(sender.getUniqueId(), 0L) < 1000) return;
        lastRequest.put(sender.getUniqueId(), now);
        requests.put(target.getUniqueId(), sender.getUniqueId());
        msg(sender, getConfig().getString("messages.request-sent").replace("%player%", target.getName()));
        msg(target, getConfig().getString("messages.request-received").replace("%player%", sender.getName()));
        target.sendMessage(net.kyori.adventure.text.Component.text("   [Annehmen]", net.kyori.adventure.text.format.NamedTextColor.GREEN)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/trade accept"))
                .append(net.kyori.adventure.text.Component.text("  [Ablehnen]", net.kyori.adventure.text.format.NamedTextColor.RED)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/trade deny"))));
        int expiry = Math.max(5, getConfig().getInt("settings.request-expire-seconds", 30));
        Bukkit.getScheduler().runTaskLater(host(), () -> { if (Objects.equals(requests.get(target.getUniqueId()), sender.getUniqueId())) { requests.remove(target.getUniqueId()); msg(target, getConfig().getString("messages.request-expired")); } }, expiry * 20L);
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return Collections.emptyList();
        String t = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : List.of("accept", "deny", "cancel", "money", "neptuns")) if (o.startsWith(t)) out.add(o);
        for (Player pl : Bukkit.getOnlinePlayers()) if (!pl.equals(sender) && pl.getName().toLowerCase(Locale.ROOT).startsWith(t)) out.add(pl.getName());
        return out;
    }

    private void accept(Player receiver) {
        UUID fromId = requests.remove(receiver.getUniqueId());
        if (fromId == null) { msg(receiver, getConfig().getString("messages.no-request")); return; }
        Player other = Bukkit.getPlayer(fromId);
        if (other == null || !other.isOnline()) { msg(receiver, "&8That player is no longer online."); return; }
        if (trades.containsKey(receiver.getUniqueId()) || trades.containsKey(other.getUniqueId())) { msg(receiver, getConfig().getString("messages.busy")); return; }
        Trade trade = new Trade(other.getUniqueId(), receiver.getUniqueId());
        trades.put(other.getUniqueId(), trade); trades.put(receiver.getUniqueId(), trade);
        openTrade(other, trade); openTrade(receiver, trade);
        msg(other, getConfig().getString("messages.trade-started").replace("%player%", receiver.getName()));
        msg(receiver, getConfig().getString("messages.trade-started").replace("%player%", other.getName()));
    }

    private void openTrade(Player p, Trade t) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&8Trade &f| &8" + name(t.other(p))));
        t.inventories.put(p.getUniqueId(), inv);
        render(p, t);
        p.openInventory(inv);
    }

    private void render(Player p, Trade t) {
        Inventory inv = t.inventories.get(p.getUniqueId()); if (inv == null) return;
        UUID otherId = t.other(p);
        for (int i = 24; i <= 44; i++) inv.setItem(i, null);
        Inventory otherInv = t.inventories.get(otherId);
        if (otherInv != null) for (int i = 0; i <= 20; i++) { ItemStack it = otherInv.getItem(i); inv.setItem(i + 24, it == null ? null : it.clone()); }
        inv.setItem(21, button(Material.GOLD_INGOT, "&fMoney: &8" + money(t.money.getOrDefault(p.getUniqueId(), 0.0)), "&8Click to set money offer", "&8Use /trade money"));
        inv.setItem(22, button(Material.PRISMARINE_CRYSTALS, "&fNeptuns: &8" + (long)t.points.getOrDefault(p.getUniqueId(), 0L), "&8Click to set Neptuns offer", "&8Use /trade neptuns"));
        inv.setItem(23, button(t.confirmed.contains(p.getUniqueId()) ? Material.LIME_DYE : Material.GRAY_DYE, t.confirmed.contains(p.getUniqueId()) ? "&fConfirmed" : "&8Confirm Trade", "&8Both players must confirm"));
        inv.setItem(45, button(Material.PAPER, "&fYour Offer", "&8Place items in slots 1-21"));
        inv.setItem(46, button(Material.PLAYER_HEAD, "&fTrading with &8" + name(otherId)));
        inv.setItem(49, button(Material.BARRIER, "&8Cancel Trade"));
        inv.setItem(50, button(Material.CLOCK, t.countdownRunning ? "&fCompleting in &8" + t.countdown : "&8Waiting for confirmation"));
    }

    @EventHandler public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = e.getView().getTitle();
        if (title.startsWith(color("&8Shulker Contents"))) { e.setCancelled(true); return; }
        Trade t = trades.get(p.getUniqueId());
        if (t == null || t.inventories.get(p.getUniqueId()) != e.getView().getTopInventory()) return;
        int slot = e.getRawSlot();
        if (slot < 0) return;
        if (slot >= 54) {
            if (t.countdownRunning) { e.setCancelled(true); return; }
            Bukkit.getScheduler().runTask(host(), () -> changed(t));
            return;
        }
        e.setCancelled(true);
        if (slot >= 24 && slot <= 44) {
            ItemStack item = e.getCurrentItem();
            if (e.getClick() == ClickType.RIGHT && isShulker(item)) showShulker(p, item);
            return;
        }
        if (slot >= 0 && slot <= 20) {
            if (t.countdownRunning) return;
            // Safely allow normal inventory movement only in own offer slots.
            e.setCancelled(false);
            Bukkit.getScheduler().runTask(host(), () -> changed(t));
            return;
        }
        if (slot == 21) { inputMode.put(p.getUniqueId(), "money"); msg(p, getConfig().getString("messages.enter-amount")); p.closeInventory(); }
        else if (slot == 22) { inputMode.put(p.getUniqueId(), "points"); msg(p, getConfig().getString("messages.enter-amount")); p.closeInventory(); }
        else if (slot == 23) toggleConfirm(p, t);
        else if (slot == 49) cancelTrade(t, "&8Trade cancelled.");
    }

    @EventHandler public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Trade t = trades.get(p.getUniqueId()); if (t == null || t.inventories.get(p.getUniqueId()) != e.getView().getTopInventory()) return;
        if (t.countdownRunning || e.getRawSlots().stream().anyMatch(s -> s < 54 && (s < 0 || s > 20))) { e.setCancelled(true); return; }
        Bukkit.getScheduler().runTask(host(), () -> changed(t));
    }

    private void changed(Trade t) {
        if (t.finished) return;
        boolean wasConfirmed = !t.confirmed.isEmpty() || t.countdownRunning;
        t.confirmed.clear(); t.countdownRunning = false; t.countdown = 0;
        for (UUID id : List.of(t.a, t.b)) { Player p = Bukkit.getPlayer(id); if (p != null) { render(p, t); if (wasConfirmed) msg(p, getConfig().getString("messages.offer-changed")); } }
    }

    private void toggleConfirm(Player p, Trade t) {
        if (t.countdownRunning) { msg(p, "&8The trade is already completing."); return; }
        if (t.confirmed.contains(p.getUniqueId())) t.confirmed.remove(p.getUniqueId()); else t.confirmed.add(p.getUniqueId());
        for (UUID id : List.of(t.a, t.b)) { Player pl = Bukkit.getPlayer(id); if (pl != null) render(pl, t); }
        if (t.confirmed.size() == 2) {
            if (!canAfford(t.a, t) || !canAfford(t.b, t)) { t.confirmed.clear(); Player a = Bukkit.getPlayer(t.a), b = Bukkit.getPlayer(t.b); if (a != null) msg(a, "&8One player cannot afford their offer."); if (b != null) msg(b, "&8One player cannot afford their offer."); changed(t); return; }
            t.countdownRunning = true; t.countdown = Math.max(1, getConfig().getInt("settings.confirmation-countdown-seconds", 5));
            countdownTick(t);
        }
    }
    private void countdownTick(Trade t) {
        if (t.finished || !t.countdownRunning) return;
        if (t.countdown <= 0) { complete(t); return; }
        for (UUID id : List.of(t.a, t.b)) { Player p = Bukkit.getPlayer(id); if (p != null) { render(p, t); msg(p, getConfig().getString("messages.countdown").replace("%seconds%", String.valueOf(t.countdown))); } }
        t.countdown--;
        Bukkit.getScheduler().runTaskLater(host(), () -> countdownTick(t), 20L);
    }

    private boolean canAfford(UUID id, Trade t) {
        Player p = Bukkit.getPlayer(id); if (p == null) return false;
        double m = t.money.getOrDefault(id, 0.0); long pts = t.points.getOrDefault(id, 0L);
        return (m <= 0 || eco.has(p, Currency.MONEY, m)) && (pts <= 0 || eco.has(p, Currency.NEPTUNS, pts));
    }

    private void complete(Trade t) {
        if (t.finished) return;
        Player a = Bukkit.getPlayer(t.a), b = Bukkit.getPlayer(t.b);
        if (a == null || b == null || !canAfford(t.a, t) || !canAfford(t.b, t)) { cancelTrade(t, "&8Trade cancelled because a player left or could not pay."); return; }
        t.finished = true;
        // Withdraw all currency before transferring items; recheck balances above.
        double am = t.money.getOrDefault(t.a, 0.0), bm = t.money.getOrDefault(t.b, 0.0);
        long ap = t.points.getOrDefault(t.a, 0L), bp = t.points.getOrDefault(t.b, 0L);
        boolean wA = am <= 0 || eco.withdraw(a, Currency.MONEY, am);
        boolean wB = bm <= 0 || eco.withdraw(b, Currency.MONEY, bm);
        boolean pA = ap <= 0 || eco.withdraw(a, Currency.NEPTUNS, ap);
        boolean pB = bp <= 0 || eco.withdraw(b, Currency.NEPTUNS, bp);
        if (!(wA && wB && pA && pB)) {
            // Roll back whatever was already taken.
            if (wA && am > 0) eco.deposit(a, Currency.MONEY, am);
            if (wB && bm > 0) eco.deposit(b, Currency.MONEY, bm);
            if (pA && ap > 0) eco.deposit(a, Currency.NEPTUNS, ap);
            if (pB && bp > 0) eco.deposit(b, Currency.NEPTUNS, bp);
            t.finished = false; cancelTrade(t, "&8Currency transaction failed. Trade cancelled."); return;
        }
        if (am > 0) eco.deposit(b, Currency.MONEY, am);
        if (bm > 0) eco.deposit(a, Currency.MONEY, bm);
        if (ap > 0) eco.deposit(b, Currency.NEPTUNS, ap);
        if (bp > 0) eco.deposit(a, Currency.NEPTUNS, bp);
        transferItems(t, a, b); transferItems(t, b, a);
        trades.remove(t.a); trades.remove(t.b);
        for (Player p : List.of(a, b)) { p.closeInventory(); msg(p, getConfig().getString("messages.trade-complete")); p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f); }
    }
    private void transferItems(Trade t, Player from, Player to) {
        Inventory inv = t.inventories.get(from.getUniqueId()); if (inv == null) return;
        for (int i = 0; i <= 20; i++) { ItemStack it = inv.getItem(i); if (it != null && it.getType() != Material.AIR) { Map<Integer, ItemStack> leftovers = to.getInventory().addItem(it.clone()); leftovers.values().forEach(left -> from.getWorld().dropItemNaturally(from.getLocation(), left)); inv.setItem(i, null); } }
    }

    private void cancelFor(Player p, String message) { Trade t = trades.get(p.getUniqueId()); if (t != null) cancelTrade(t, message); else msg(p, "&8You are not in a trade."); }
    private void cancelTrade(Trade t, String message) {
        if (t.finished) return;
        t.finished = true;
        for (UUID id : List.of(t.a, t.b)) {
            Player p = Bukkit.getPlayer(id); Inventory inv = t.inventories.get(id);
            if (p != null) {
                if (inv != null) for (int i = 0; i <= 20; i++) { ItemStack it = inv.getItem(i); if (it != null && it.getType() != Material.AIR) { Map<Integer, ItemStack> left = p.getInventory().addItem(it.clone()); left.values().forEach(x -> p.getWorld().dropItemNaturally(p.getLocation(), x)); inv.setItem(i, null); } }
                p.closeInventory(); msg(p, message);
            }
            trades.remove(id); inputMode.remove(id);
        }
    }

    @EventHandler public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (e.getView().getTitle().startsWith(color("&8Shulker Contents"))) {
            previewing.remove(p.getUniqueId());
            Bukkit.getScheduler().runTask(host(), () -> reopen(p));
            return;
        }
        if (previewing.contains(p.getUniqueId()) || inputMode.containsKey(p.getUniqueId())) return;
        Trade t = trades.get(p.getUniqueId()); if (t == null || t.finished || t.inventories.get(p.getUniqueId()) != e.getInventory()) return;
        // Closing the GUI cancels safely and returns all offered items.
        Bukkit.getScheduler().runTask(host(), () -> { if (trades.get(p.getUniqueId()) == t && !t.finished) cancelTrade(t, "&8Trade cancelled because a trade window was closed."); });
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer(); requests.remove(p.getUniqueId());
        Trade t = trades.get(p.getUniqueId()); if (t != null) cancelTrade(t, getConfig().getString("messages.player-left"));
        inputMode.remove(p.getUniqueId());
    }

    @EventHandler public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer(); String mode = inputMode.get(p.getUniqueId()); if (mode == null) return;
        e.setCancelled(true); String text = e.getMessage().trim();
        Bukkit.getScheduler().runTask(host(), () -> {
            if (text.equalsIgnoreCase("cancel")) { inputMode.remove(p.getUniqueId()); msg(p, "&8Cancelled."); reopen(p); return; }
            double amount;
            try { amount = Double.parseDouble(text); if (!Double.isFinite(amount) || amount < 0 || amount > 1_000_000_000_000d) throw new NumberFormatException(); }
            catch (NumberFormatException ex) { msg(p, getConfig().getString("messages.invalid-amount")); return; }
            Trade t = trades.get(p.getUniqueId()); if (t == null) { inputMode.remove(p.getUniqueId()); return; }
            if (mode.equals("money")) { if (amount > 0 && !eco.isAvailable(Currency.MONEY)) { msg(p, "&8Vault economy is not available."); return; } t.money.put(p.getUniqueId(), amount); }
            else { if (amount != Math.floor(amount)) { msg(p, "&8Neptuns must be a whole number."); return; } if (amount > 0 && !eco.isAvailable(Currency.NEPTUNS)) { msg(p, "&8PlayerPoints is not available."); return; } t.points.put(p.getUniqueId(), (long) amount); }
            inputMode.remove(p.getUniqueId()); changed(t); reopen(p);
        });
    }
    private void reopen(Player p) { Trade t = trades.get(p.getUniqueId()); if (t != null && !t.finished) { Inventory inv = t.inventories.get(p.getUniqueId()); if (inv != null) p.openInventory(inv); } }

    private void showShulker(Player p, ItemStack item) {
        if (!(item.getItemMeta() instanceof BlockStateMeta meta) || !(meta.getBlockState() instanceof ShulkerBox box)) return;
        Inventory preview = Bukkit.createInventory(null, 27, color("&8Shulker Contents &f| &8Preview"));
        for (int i = 0; i < Math.min(27, box.getInventory().getSize()); i++) { ItemStack it = box.getInventory().getItem(i); if (it != null) preview.setItem(i, it.clone()); }
        previewing.add(p.getUniqueId());
        p.openInventory(preview);
    }
    private boolean isShulker(ItemStack it) { return it != null && it.getType().name().endsWith("SHULKER_BOX"); }
    private ItemStack button(Material material, String name, String... lore) {
        ItemStack it = new ItemStack(material); ItemMeta meta = it.getItemMeta(); meta.setDisplayName(color(name));
        if (lore.length > 0) meta.setLore(Arrays.stream(lore).map(this::color).toList()); it.setItemMeta(meta); return it;
    }
    private String name(UUID id) { OfflinePlayer p = Bukkit.getOfflinePlayer(id); return p.getName() == null ? "Player" : p.getName(); }
    private String money(double d) { return String.format(Locale.US, "%.2f", d); }
    private String color(String s) { return org.bukkit.ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
    private void msg(Player p, String s) { p.sendMessage(prefix + color(s)); }
    private static final class Trade {
        final UUID a, b; final Map<UUID, Inventory> inventories = new HashMap<>(); final Map<UUID, Double> money = new HashMap<>(); final Map<UUID, Long> points = new HashMap<>(); final Set<UUID> confirmed = new HashSet<>(); boolean countdownRunning, finished; int countdown;
        Trade(UUID a, UUID b) { this.a = a; this.b = b; }
        UUID other(Player p) { return p.getUniqueId().equals(a) ? b : a; }
    }
}
