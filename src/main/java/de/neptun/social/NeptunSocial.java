package de.neptun.social;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import de.neptun.core.Module;
import de.neptun.core.NeptunCore;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class NeptunSocial extends Module implements Listener, TabExecutor {
    public NeptunSocial(NeptunCore host) { super(host, "social"); }
    @Override public String[] commands() { return new String[]{"friends", "settings", "friendsread", "friendsgive"}; }
    @Override public String legacyFolder() { return "NeptunSocial"; }
    private File friendsFile, requestsFile, messagesFile, giftsFile, historyFile, settingsFile;
    private YamlConfiguration friends, requests, messages, gifts, history, settings;
    private final Map<UUID, String> activeGiftTarget = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> giftMenuOwners = new ConcurrentHashMap<>();
    private final Map<UUID, String> guiModes = new ConcurrentHashMap<>();
    private NamespacedKey actionKey;

    @Override public void onEnable() {
        saveDefaultConfig();
        friendsFile = file("friends.yml"); requestsFile = file("requests.yml");
        messagesFile = file("messages.yml"); giftsFile = file("gifts.yml");
        historyFile = file("gift-history.yml"); settingsFile = file("settings.yml");
        friends = load(friendsFile); requests = load(requestsFile); messages = load(messagesFile);
        gifts = load(giftsFile); history = load(historyFile); settings = load(settingsFile);
        actionKey = new NamespacedKey(host(), "social_action");
        Bukkit.getPluginManager().registerEvents(this, host());
        for (String cmd : List.of("friends", "settings", "friendsread", "friendsgive")) {
            Objects.requireNonNull(getCommand(cmd)).setExecutor(this);
            Objects.requireNonNull(getCommand(cmd)).setTabCompleter(this);
        }
        getLogger().info("NeptunSocial enabled.");
    }

    private File file(String name) { File f = new File(getDataFolder(), name); if (!getDataFolder().exists()) getDataFolder().mkdirs(); return f; }
    private YamlConfiguration load(File f) { return YamlConfiguration.loadConfiguration(f); }
    private void save(YamlConfiguration y, File f) { try { y.save(f); } catch (IOException e) { getLogger().severe("Could not save " + f.getName() + ": " + e.getMessage()); } }
    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
    private String prefix() { return color(getConfig().getString("messages.prefix", "&8[&fNeptun&8] ")); }
    private void msg(CommandSender s, String m) { s.sendMessage(prefix() + color(m)); }
    private String key(UUID u) { return u.toString(); }
    private String playerName(UUID u) { OfflinePlayer p = Bukkit.getOfflinePlayer(u); return p.getName() == null ? u.toString().substring(0, 8) : p.getName(); }
    private List<String> list(YamlConfiguration y, String path) { return y.getStringList(path); }
    private boolean areFriends(UUID a, UUID b) { return list(friends, key(a)).contains(key(b)); }
    private void addFriend(UUID a, UUID b) {
        List<String> al = new ArrayList<>(list(friends, key(a))); if (!al.contains(key(b))) al.add(key(b)); friends.set(key(a), al);
        List<String> bl = new ArrayList<>(list(friends, key(b))); if (!bl.contains(key(a))) bl.add(key(a)); friends.set(key(b), bl);
        save(friends, friendsFile);
    }
    private void removeFriend(UUID a, UUID b) {
        List<String> al = new ArrayList<>(list(friends, key(a))); al.remove(key(b)); friends.set(key(a), al);
        List<String> bl = new ArrayList<>(list(friends, key(b))); bl.remove(key(a)); friends.set(key(b), bl); save(friends, friendsFile);
    }
    /** Fuer das chatitem-Modul: hat der Spieler "Item display" in /settings an? */
    public boolean itemDisplay(UUID u) { return setting(u, "item-display"); }
    private boolean setting(UUID u, String s) { return settings.getBoolean(key(u) + "." + s, getConfig().getBoolean("settings-defaults." + s, true)); }
    private void toggleSetting(Player p, String s) { settings.set(key(p.getUniqueId()) + "." + s, !setting(p.getUniqueId(), s)); save(settings, settingsFile); openSettings(p); }
    private ItemStack item(Material mat, String name, String action, String... lore) {
        ItemStack i = new ItemStack(mat); ItemMeta m = i.getItemMeta(); m.setDisplayName(color(name));
        if (lore.length > 0) m.setLore(Arrays.stream(lore).map(this::color).toList());
        if (action != null) m.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        i.setItemMeta(m); return i;
    }
    private String action(ItemStack i) { if (i == null || !i.hasItemMeta()) return null; return i.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING); }
    private void openFriends(Player p, String search) {
        List<UUID> ids = list(friends, key(p.getUniqueId())).stream().map(s -> { try { return UUID.fromString(s); } catch (Exception e) { return null; } }).filter(Objects::nonNull).filter(u -> search == null || playerName(u).toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))).toList();
        Inventory inv = Bukkit.createInventory(null, 54, color("&8Friends"));
        int slot = 0;
        for (UUID id : ids) {
            if (slot >= 45) break;
            Player online = Bukkit.getPlayer(id);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            var meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(id));
            meta.setDisplayName(color((online != null ? "&f" : "&8") + playerName(id)));
            meta.setLore(List.of(color("&8Right-click to manage"), color(online != null ? "&fOnline" : "&8Offline")));
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "friend:" + id);
            head.setItemMeta(meta); inv.setItem(slot++, head);
        }
        inv.setItem(45, item(Material.COMPASS, "&fSearch friends", "search", "&8Click to search by name"));
        inv.setItem(49, item(Material.WRITABLE_BOOK, "&fSettings", "settings", "&8Personal preferences"));
        inv.setItem(53, item(Material.BARRIER, "&8Close", "close"));
        p.openInventory(inv); guiModes.put(p.getUniqueId(), "friends");
    }
    private void openFriendManage(Player p, UUID friend) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&8Friend: &f" + playerName(friend)));
        inv.setItem(11, item(Material.BOOK, "&fRead saved messages", "read:" + friend, "&8View messages saved from this friend"));
        inv.setItem(13, item(Material.NETHER_STAR, "&fToggle priority", "priority:" + friend, "&8Prioritised friends appear first"));
        inv.setItem(15, item(Material.RED_DYE, "&8Remove friend", "remove:" + friend, "&8Remove this player from your friends"));
        inv.setItem(22, item(Material.ARROW, "&fBack", "back", "&8Return to friends"));
        p.openInventory(inv); guiModes.put(p.getUniqueId(), "friendmanage:" + friend);
    }
    private void openSettings(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&8Settings"));
        String[] ids = {"join-message","leave-message","payments","tpa","chat-messages","friend-messages","friend-gifts","item-display"};
        String[] labels = {"Join messages","Leave messages","Payments","TPA requests","Chat messages","Friend messages","Friend gifts","Item display"};
        Material[] mats = {Material.OAK_DOOR,Material.IRON_DOOR,Material.GOLD_INGOT,Material.ENDER_PEARL,Material.PAPER,Material.WRITABLE_BOOK,Material.CHEST,Material.ITEM_FRAME};
        for (int n=0;n<ids.length;n++) {
            String id=ids[n]; boolean on=setting(p.getUniqueId(), id);
            inv.setItem(n, item(mats[n], (on?"&f":"&8") + labels[n], "toggle:" + id, "&8Status: " + (on?"&fEnabled":"&8Disabled"), "&8Click to toggle"));
        }
        inv.setItem(22, item(Material.ARROW, "&fBack", "back", "&8Return to friends"));
        p.openInventory(inv); guiModes.put(p.getUniqueId(), "settings");
    }
    private void openGiftDeposit(Player sender, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) { msg(sender, "&8That player must be online to receive a gift."); return; }
        if (!areFriends(sender.getUniqueId(), target.getUniqueId())) { msg(sender, "&8You can only send gifts to friends."); return; }
        Inventory inv = Bukkit.createInventory(null, 27, color("&8Gift for &f" + target.getName()));
        inv.setItem(22, item(Material.LIME_DYE, "&fSend gift", "gift-send:" + target.getUniqueId(), "&8Confirm to send all items in this menu"));
        activeGiftTarget.put(sender.getUniqueId(), targetName); sender.openInventory(inv); guiModes.put(sender.getUniqueId(), "giftdeposit");
    }
    private void openGiftInbox(Player p, boolean historyView) {
        String path = key(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, color(historyView ? "&8Gift History" : "&8Gift Inbox"));
        List<Map<?,?>> entries = gifts.getMapList(path);
        if (historyView) entries = history.getMapList(path);
        int slot=0;
        for (Map<?,?> entry : entries) {
            if (slot >= 45) break;
            Object raw=entry.get("item"); if (!(raw instanceof ItemStack stack)) continue;
            ItemStack shown=stack.clone(); ItemMeta m=shown.getItemMeta();
            List<String> lore=m.hasLore()?new ArrayList<>(m.getLore()):new ArrayList<>();
            lore.add(color("&8From: &f" + String.valueOf(entry.containsKey("from") ? entry.get("from") : "Unknown")));
            if (historyView) lore.add(color("&8Already claimed"));
            else lore.add(color("&fClick to claim"));
            m.setLore(lore); shown.setItemMeta(m);
            shown.setAmount(Math.max(1, Math.min(64, shown.getAmount())));
            inv.setItem(slot, shown); slot++;
        }
        inv.setItem(49, item(Material.ARROW, "&fBack", "back", "&8Return to friends"));
        p.openInventory(inv); guiModes.put(p.getUniqueId(), historyView ? "gifthistory" : "giftinbox");
    }
    private void saveMessage(UUID receiver, UUID sender, String text) {
        String path=key(receiver); List<String> l=new ArrayList<>(messages.getStringList(path)); l.add("[" + playerName(sender) + "] " + text); messages.set(path,l); save(messages,messagesFile);
    }
    private void readMessages(Player p, UUID from) {
        String path=key(p.getUniqueId()); List<String> all=new ArrayList<>(messages.getStringList(path));
        List<String> selected=all.stream().filter(s -> from == null || s.startsWith("[" + playerName(from) + "]")).toList();
        if (selected.isEmpty()) { msg(p, "&8No saved friend messages."); return; }
        msg(p, "&fSaved friend messages:");
        selected.forEach(s -> p.sendMessage(color("&8" + s)));
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        String cmd=command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("settings")) { openSettings(p); return true; }
        if (cmd.equals("friendsread")) { readMessages(p,null); return true; }
        if (cmd.equals("friendsgive")) {
            if (args.length==1 && args[0].equalsIgnoreCase("see")) { openGiftInbox(p,false); return true; }
            if (args.length>=2 && args[0].equalsIgnoreCase("see") && args[1].equalsIgnoreCase("history")) { openGiftInbox(p,true); return true; }
            if (args.length>=1) { openGiftDeposit(p,args[0]); return true; }
            msg(p,"&8Usage: /friends give <player|see|see history>"); return true;
        }
        if (args.length==0) { openFriends(p,null); return true; }
        if (args[0].equalsIgnoreCase("add") && args.length>=2) {
            Player target=Bukkit.getPlayerExact(args[1]);
            if (target==null) { msg(p,"&8That player must be online to receive a request."); return true; }
            if (target.equals(p)) { msg(p,"&8You cannot add yourself."); return true; }
            requests.set(key(target.getUniqueId()) + "." + key(p.getUniqueId()), p.getName()); save(requests,requestsFile);
            msg(p,"&fFriend request sent to &8" + target.getName());
            msg(target,"&f" + p.getName() + " &8sent you a friend request. Use &f/friends accept " + p.getName());
            return true;
        }
        if (args[0].equalsIgnoreCase("accept") && args.length>=2) {
            Player target=Bukkit.getPlayerExact(args[1]); if(target==null){msg(p,"&8That player is offline.");return true;}
            String path=key(p.getUniqueId())+"."+key(target.getUniqueId());
            if (!requests.contains(path)) { msg(p,"&8No pending request from that player."); return true; }
            requests.set(path,null); save(requests,requestsFile); addFriend(p.getUniqueId(),target.getUniqueId());
            msg(p,"&fYou are now friends with &8"+target.getName()); msg(target,"&f"+p.getName()+" &8accepted your friend request."); return true;
        }
        if (args[0].equalsIgnoreCase("remove") && args.length>=2) {
            OfflinePlayer target=Bukkit.getOfflinePlayer(args[1]); if(target.getUniqueId()==null || !areFriends(p.getUniqueId(),target.getUniqueId())) {msg(p,"&8That player is not your friend.");return true;}
            removeFriend(p.getUniqueId(),target.getUniqueId()); msg(p,"&8Friend removed."); return true;
        }
        if (args[0].equalsIgnoreCase("read")) { readMessages(p,null); return true; }
        if (args[0].equalsIgnoreCase("give")) {
            if(args.length==2 && args[1].equalsIgnoreCase("see")) {openGiftInbox(p,false);return true;}
            if(args.length>=3 && args[1].equalsIgnoreCase("see") && args[2].equalsIgnoreCase("history")) {openGiftInbox(p,true);return true;}
            if(args.length>=2) {openGiftDeposit(p,args[1]);return true;}
        }
        if (args[0].equalsIgnoreCase("search") && args.length>=2) { openFriends(p,String.join(" ",Arrays.copyOfRange(args,1,args.length))); return true; }
        msg(p,"&8Usage: /friends [add|accept|remove|read|give|search]"); return true;
    }
    @Override public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (args.length==1) return List.of("add","accept","remove","read","give","search");
        if (args.length==2 && List.of("add","accept","remove","give").contains(args[0].toLowerCase(Locale.ROOT))) return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n->n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        return Collections.emptyList();
    }
    @EventHandler public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title=e.getView().getTitle();
        if (title.equals(color("&8Gift for &f"+activeGiftTarget.getOrDefault(p.getUniqueId(),"")))) {
            if (e.getRawSlot() >= 0 && e.getRawSlot() < e.getView().getTopInventory().getSize()) {
                if (e.getRawSlot()==22 && action(e.getCurrentItem()) != null && action(e.getCurrentItem()).startsWith("gift-send:")) {
                    e.setCancelled(true);
                    String targetName=activeGiftTarget.remove(p.getUniqueId()); Player target=Bukkit.getPlayerExact(targetName);
                    if(target==null){msg(p,"&8The recipient went offline.");p.closeInventory();return;}
                    List<Map<?,?>> list=new ArrayList<>(gifts.getMapList(key(target.getUniqueId())));
                    for(ItemStack stack:e.getView().getTopInventory().getContents()) {
                        if(stack==null || stack.getType()==Material.AIR || action(stack)!=null) continue;
                        Map<String,Object> m=new HashMap<>();m.put("item",stack.clone());m.put("from",p.getName());list.add(m);
                    }
                    gifts.set(key(target.getUniqueId()),list);save(gifts,giftsFile);
                    msg(p,"&fGift sent to &8"+target.getName());msg(target,"&fYou received a gift from &8"+p.getName()+". Use &f/friends give see");
                    p.closeInventory();return;
                }
                if (e.getRawSlot() >= 27) e.setCancelled(true);
            }
            return;
        }
        if (title.equals(color("&8Gift Inbox"))) {
            e.setCancelled(true); if(e.getRawSlot()>=0 && e.getRawSlot()<45) {
                List<Map<?,?>> entries=new ArrayList<>(gifts.getMapList(key(p.getUniqueId())));
                if(e.getRawSlot()<entries.size()) {
                    Map<?,?> entry=entries.get(e.getRawSlot()); Object raw=entry.get("item");
                    if(raw instanceof ItemStack stack) {
                        HashMap<Integer,ItemStack> leftover=p.getInventory().addItem(stack.clone());
                        if(!leftover.isEmpty()){msg(p,"&8Your inventory is full.");return;}
                        entries.remove(e.getRawSlot());gifts.set(key(p.getUniqueId()),entries);save(gifts,giftsFile);
                        List<Map<String,Object>> hist=new ArrayList<>();for(Map<?,?> x:history.getMapList(key(p.getUniqueId()))) hist.add(new HashMap<>((Map)x));
                        hist.add(new HashMap<>((Map)entry));history.set(key(p.getUniqueId()),hist);save(history,historyFile);openGiftInbox(p,false);
                    }
                }
            } else if ("back".equals(action(e.getCurrentItem()))) { e.setCancelled(true);openFriends(p,null); }
            return;
        }
        if (title.equals(color("&8Gift History"))) { e.setCancelled(true); if("back".equals(action(e.getCurrentItem()))) openFriends(p,null); return; }
        if (title.equals(color("&8Friends")) || title.equals(color("&8Settings")) || title.startsWith(color("&8Friend:"))) {
            e.setCancelled(true); String a=action(e.getCurrentItem()); if(a==null)return;
            if(a.startsWith("friend:")) { try{openFriendManage(p,UUID.fromString(a.substring(7)));}catch(Exception ignored){} }
            else if(a.startsWith("remove:")) { try{removeFriend(p.getUniqueId(),UUID.fromString(a.substring(7)));msg(p,"&8Friend removed.");openFriends(p,null);}catch(Exception ignored){} }
            else if(a.startsWith("priority:")) { UUID id=UUID.fromString(a.substring(9)); String path=key(p.getUniqueId())+".priority";List<String> l=new ArrayList<>(settings.getStringList(path));if(l.contains(key(id)))l.remove(key(id));else l.add(key(id));settings.set(path,l);save(settings,settingsFile);msg(p,"&fFriend priority updated.");openFriendManage(p,id); }
            else if(a.startsWith("read:")) { readMessages(p,UUID.fromString(a.substring(5))); }
            else if(a.startsWith("toggle:")) toggleSetting(p,a.substring(7));
            else if(a.equals("settings")) openSettings(p);
            else if(a.equals("back")) openFriends(p,null);
            else if(a.equals("search")) { msg(p,"&8Use /friends search <name> to search friends.");p.closeInventory(); }
            else if(a.equals("close")) p.closeInventory();
        }
    }
    @EventHandler public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (guiModes.getOrDefault(p.getUniqueId(),"").equals("giftdeposit") && e.getView().getTitle().startsWith(color("&8Gift for "))) {
            // Return deposited items if they close without confirming. A sent gift has already been persisted.
            if (activeGiftTarget.containsKey(p.getUniqueId())) {
                for(ItemStack i:e.getView().getTopInventory().getContents()) if(i!=null && i.getType()!=Material.AIR && action(i)==null) {
                    HashMap<Integer,ItemStack> leftovers=p.getInventory().addItem(i.clone());
                    leftovers.values().forEach(item -> p.getWorld().dropItemNaturally(p.getLocation(),item));
                }
                activeGiftTarget.remove(p.getUniqueId());
            }
        }
        guiModes.remove(p.getUniqueId());
    }
    @EventHandler public void onJoin(PlayerJoinEvent e) {
        Player p=e.getPlayer();
        if (!setting(p.getUniqueId(),"join-message")) e.setJoinMessage(null);
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) {
        Player p=e.getPlayer();
        if (!setting(p.getUniqueId(),"leave-message")) e.setQuitMessage(null);
    }
    @EventHandler public void onChat(AsyncPlayerChatEvent e) {
        Player p=e.getPlayer(); String message=e.getMessage();
        // [i] wird jetzt vom Modul "chatitem" behandelt (Paper-Chat-Event, volle Item-Anzeige).
        if (!setting(p.getUniqueId(),"chat-messages")) e.setCancelled(true);
        if (!setting(p.getUniqueId(),"friend-messages") && message.startsWith("/msg ")) e.setCancelled(true);
        if(message.startsWith("/msg ") && setting(p.getUniqueId(),"friend-messages")) {
            String[] parts=message.split(" ",3);
            if(parts.length>=3) {
                Player receiver=Bukkit.getPlayerExact(parts[1]);
                if(receiver!=null && areFriends(p.getUniqueId(),receiver.getUniqueId())) saveMessage(receiver.getUniqueId(),p.getUniqueId(),parts[2]);
            }
        }
    }
}
