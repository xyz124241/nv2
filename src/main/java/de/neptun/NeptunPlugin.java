package de.neptun;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import de.neptun.core.Module;
import de.neptun.core.NeptunCore;
import org.bukkit.Particle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Sound;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class NeptunPlugin extends Module implements Listener {
    public NeptunPlugin(NeptunCore host) { super(host, "neptun"); }
    @Override public String[] commands() { return new String[]{"spawn", "setspawn", "home", "sethome", "delhome", "rtp"}; }
    @Override public String legacyFolder() { return "Neptun"; }
    private final Map<UUID, Map<String, Location>> homes = new HashMap<>();
    private final Map<UUID, Long> rtpCooldowns = new HashMap<>();
    private final Map<UUID, BukkitTask> rtpTasks = new HashMap<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        loadHomes();
        Bukkit.getPluginManager().registerEvents(this, host());
        getLogger().info("[neptun] Spawn/Homes/RTP aktiv.");
    }

    @Override public void onDisable() {
        for (BukkitTask t : rtpTasks.values()) t.cancel();
        rtpTasks.clear();
        saveHomes();
    }

    private String msg(String s) {
        return ChatColor.translateAlternateColorCodes('&', getConfig().getString("prefix", "&8[&f&lNeptun&8] &f") + s);
    }
    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
    private void tell(CommandSender sender, String s) { sender.sendMessage(msg(s)); }
    private boolean isAdmin(Player p) { return p.hasPermission("neptun.admin"); }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { tell(sender, "&cNur im Spiel verfügbar."); return true; }
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "spawn" -> {
                Location loc = getConfig().getLocation("spawns." + p.getWorld().getName());
                if (loc == null) loc = p.getWorld().getSpawnLocation();
                p.teleportAsync(loc).thenAccept(ok -> { if (ok) tell(p, "&fZum Spawn teleportiert."); });
            }
            case "setspawn" -> {
                if (!p.hasPermission("neptun.setspawn")) { tell(p, "&cKeine Berechtigung."); return true; }
                getConfig().set("spawns." + p.getWorld().getName(), p.getLocation());
                saveConfig(); tell(p, "&fSpawn für &l" + p.getWorld().getName() + " &fgesetzt.");
            }
            case "sethome" -> {
                if (args.length != 1) { tell(p, "&fVerwendung: &8/sethome <name>"); return true; }
                String name = cleanName(args[0]);
                Map<String, Location> map = homes.computeIfAbsent(p.getUniqueId(), k -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
                int limit = homeLimit(p);
                if (!map.containsKey(name) && map.size() >= limit) { tell(p, "&cDu hast dein Home-Limit erreicht (&f" + limit + "&c)."); return true; }
                map.put(name, p.getLocation()); saveHomes();
                tell(p, "&fHome &8&l" + name + " &fgespeichert.");
            }
            case "home" -> {
                if (args.length > 0) teleportHome(p, args[0]); else openHomes(p, false);
            }
            case "delhome" -> {
                if (args.length > 0) {
                    Map<String, Location> map = homes.get(p.getUniqueId());
                    if (map != null && map.remove(args[0]) != null) { saveHomes(); tell(p, "&fHome gelöscht."); }
                    else tell(p, "&cDieses Home gibt es nicht.");
                } else openHomes(p, true);
            }
            case "rtp" -> {
                if (getConfig().getBoolean("rtp.menu.enabled", false)) openRtp(p); else startRtp(p, p.getWorld());
            }
        }
        return true;
    }

    private String cleanName(String n) {
        String s = n.replaceAll("[^A-Za-z0-9_-]", "");
        return s.length() > 24 ? s.substring(0, 24) : s;
    }
    private int homeLimit(Player p) {
        return getConfig().getInt("homes.limits." + effectiveRank(p), getConfig().getInt("homes.default-limit", 3));
    }
    private void teleportHome(Player p, String name) {
        Map<String, Location> map = homes.get(p.getUniqueId());
        Location loc = map == null ? null : map.get(name);
        if (loc == null || loc.getWorld() == null) { tell(p, "&cHome nicht gefunden."); return; }
        p.closeInventory();
        p.teleportAsync(loc).thenAccept(ok -> { if (ok) tell(p, "&fZum Home &8&l" + name + " &fteleportiert."); });
    }
    private void openHomes(Player p, boolean delete) {
        Map<String, Location> map = homes.getOrDefault(p.getUniqueId(), Collections.emptyMap());
        Inventory inv = Bukkit.createInventory(null, 54, color(delete ? "&8&lHome löschen" : getConfig().getString("homes-menu.title", "&8&lDeine Homes")));
        int slot = 0;
        for (String name : map.keySet()) {
            if (slot >= inv.getSize()) break;
            ItemStack item = new ItemStack(delete ? Material.BARRIER : Material.ENDER_PEARL);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(color("&f&l" + name));
            meta.setLore(List.of(color(delete ? "&8Klicken zum Löschen" : "&8Klicken zum Teleportieren")));
            item.setItemMeta(meta); inv.setItem(slot++, item);
        }
        if (map.isEmpty()) inv.setItem(22, named(Material.PAPER, "&f&lKeine Homes", "&8Nutze &f/sethome <name>"));
        p.openInventory(inv);
        p.setMetadata("neptun_delete_menu", new org.bukkit.metadata.FixedMetadataValue(host(), delete));
    }
    private ItemStack named(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat); ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(color(name));
        if (lore.length > 0) meta.setLore(Arrays.stream(lore).map(this::color).toList());
        it.setItemMeta(meta); return it;
    }
    @EventHandler public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = e.getView().getTitle();
        if (title.equals(color(getConfig().getString("homes-menu.title", "&8&lDeine Homes"))) ||
            title.equals(color("&8&lHome löschen"))) {
            e.setCancelled(true);
            if (e.getCurrentItem()==null || !e.getCurrentItem().hasItemMeta()) return;
            String name = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
            if (name == null || name.isBlank() || name.equals("Keine Homes")) return;
            boolean delete = p.hasMetadata("neptun_delete_menu") && p.getMetadata("neptun_delete_menu").get(0).asBoolean();
            if (delete) {
                Map<String,Location> map=homes.get(p.getUniqueId());
                if (map!=null && map.remove(name)!=null) { saveHomes(); tell(p, "&fHome &8&l"+name+" &fgelöscht."); }
                openHomes(p,true);
            } else teleportHome(p,name);
        } else if (title.equals(color(getConfig().getString("rtp-menu.title", "&8&lZufalls-Teleport")))) {
            e.setCancelled(true);
            if (e.getCurrentItem()==null || !e.getCurrentItem().hasItemMeta()) return;
            String worldName=ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
            World w=Bukkit.getWorld(worldName);
            if (w!=null) startRtp(p,w);
        }
    }
    // ------------------------------------------------------------------ RTP

    private static final Set<Material> RTP_UNSAFE = EnumSet.of(Material.MAGMA_BLOCK, Material.CACTUS, Material.FIRE,
            Material.SOUL_FIRE, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.SWEET_BERRY_BUSH,
            Material.POWDER_SNOW, Material.WITHER_ROSE, Material.BEDROCK, Material.LAVA, Material.WATER);

    /** Ränge aus rtp.ranks, sortiert nach priority (kleinste Zahl = bester Rang). */
    private List<String> rankOrder() {
        ConfigurationSection sec = getConfig().getConfigurationSection("rtp.ranks");
        List<String> names = new ArrayList<>();
        if (sec != null) {
            names.addAll(sec.getKeys(false));
        }
        names.sort(Comparator.comparingInt(r -> getConfig().getInt("rtp.ranks." + r + ".priority", 99)));
        return names;
    }

    /** LuckPerms setzt fuer jede Gruppe (auch geerbte) die Permission group.<name>. */
    private boolean hasRank(Player p, String rank) {
        return p.hasPermission("group." + rank) || p.hasPermission("neptun.rank." + rank);
    }

    /** Bester Rang des Spielers. Wer nur "member" erbt (z.B. Owner/Sup ohne eigenen Eintrag), bekommt member. */
    private String effectiveRank(Player p) {
        for (String r : rankOrder()) {
            if (hasRank(p, r)) {
                return r;
            }
        }
        return "member";
    }

    private boolean rtpWorldEnabled(World w) {
        return getConfig().getBoolean("rtp.worlds." + w.getName() + ".enabled", w.getEnvironment() == World.Environment.NORMAL);
    }

    private void openRtp(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, color(getConfig().getString("rtp-menu.title", "&8&lZufalls-Teleport")));
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, named(Material.GRAY_STAINED_GLASS_PANE, "&8 "));
        }
        boolean currentOnly = getConfig().getBoolean("rtp.current-world-only", true);
        int[] normalSlots = {13, 12, 14, 22, 4};
        int[] netherSlots = {11, 10};
        int[] endSlots = {15, 16};
        int n = 0, ne = 0, en = 0;
        for (World w : Bukkit.getWorlds()) {
            if (!rtpWorldEnabled(w)) {
                continue;
            }
            if (currentOnly && !w.equals(p.getWorld())) {
                continue;
            }
            World.Environment env = w.getEnvironment();
            int slot;
            Material icon;
            if (env == World.Environment.NETHER) {
                if (ne >= netherSlots.length) continue;
                slot = netherSlots[ne++];
                icon = Material.NETHERRACK;
            } else if (env == World.Environment.THE_END) {
                if (en >= endSlots.length) continue;
                slot = endSlots[en++];
                icon = Material.END_STONE;
            } else {
                if (n >= normalSlots.length) continue;
                slot = normalSlots[n++];
                icon = Material.GRASS_BLOCK;
            }
            inv.setItem(slot, named(icon, "&f&l" + w.getName(), "&8Klicken für Zufalls-Teleport"));
        }
        p.openInventory(inv);
    }

    private void stopRtp(UUID id) {
        BukkitTask t = rtpTasks.remove(id);
        if (t != null) {
            t.cancel();
        }
    }

    private static String formatSeconds(long s) {
        return s >= 60 ? (s / 60) + "m " + (s % 60) + "s" : s + "s";
    }

    private void startRtp(Player p, World w) {
        if ((w.getEnvironment() == World.Environment.NETHER && getConfig().getBoolean("dimensions.disable-nether")) ||
            (w.getEnvironment() == World.Environment.THE_END && getConfig().getBoolean("dimensions.disable-end"))) {
            if (!p.hasPermission("neptun.bypass.dimension")) { tell(p, "&cDiese Dimension ist deaktiviert."); return; }
        }
        if (!rtpWorldEnabled(w)) {
            tell(p, "&cIn dieser Welt ist RTP nicht erlaubt.");
            return;
        }
        if (getConfig().getBoolean("rtp.current-world-only", true) && !w.equals(p.getWorld())) {
            tell(p, "&cRTP geht nur in der Welt, in der du gerade bist.");
            return;
        }
        UUID id = p.getUniqueId();
        if (rtpTasks.containsKey(id)) {
            tell(p, "&cDein Teleport läuft bereits.");
            return;
        }
        String rank = effectiveRank(p);
        long now = System.currentTimeMillis();
        long until = rtpCooldowns.getOrDefault(id, 0L);
        if (!p.hasPermission("neptun.bypass.cooldown") && until > now) {
            tell(p, "&cBitte warte noch &f" + formatSeconds((until - now + 999) / 1000) + "&c.");
            return;
        }
        p.closeInventory();
        int delay = Math.max(0, getConfig().getInt("rtp.delay-seconds", 5));
        if (delay == 0) {
            searchSpot(p, w, rank);
            return;
        }
        boolean cancelOnMove = getConfig().getBoolean("rtp.cancel-on-move", true);
        Location start = p.getLocation().clone();
        tell(p, "&fTeleport in &l" + delay + "s&f." + (cancelOnMove ? " Bewege dich nicht!" : ""));
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(host(), new Runnable() {
            int left = delay;
            @Override public void run() {
                if (!p.isOnline()) { stopRtp(id); return; }
                if (cancelOnMove) {
                    Location c = p.getLocation();
                    if (!c.getWorld().equals(start.getWorld()) || c.getBlockX() != start.getBlockX()
                            || c.getBlockY() != start.getBlockY() || c.getBlockZ() != start.getBlockZ()) {
                        stopRtp(id);
                        p.sendActionBar(Component.text("Teleport abgebrochen", NamedTextColor.RED));
                        tell(p, "&cTeleport abgebrochen, weil du dich bewegt hast.");
                        return;
                    }
                }
                if (left <= 0) {
                    stopRtp(id);
                    searchSpot(p, w, rank);
                    return;
                }
                p.sendActionBar(Component.text("Teleport in " + left + "s ...", NamedTextColor.AQUA));
                left--;
            }
        }, 0L, 20L);
        rtpTasks.put(id, task);
    }

    private void searchSpot(Player p, World w, String rank) {
        String path = "rtp.ranks." + rank;
        int a = getConfig().getInt(path + ".min-radius", 500);
        int b = getConfig().getInt(path + ".max-radius", 10000);
        int min = Math.max(0, Math.min(a, b));
        int max = Math.max(min + 1, Math.max(a, b));
        tell(p, "&fSuche einen sicheren Ort...");
        trySpot(p, w, rank, min, max, Math.max(1, getConfig().getInt("rtp.max-attempts", 40)));
    }

    private void trySpot(Player p, World w, String rank, int min, int max, int left) {
        if (!p.isOnline()) {
            return;
        }
        if (left <= 0) {
            tell(p, "&cKein sicherer Ort gefunden. Versuche es erneut.");
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double angle = r.nextDouble() * Math.PI * 2;
        double dist = min + r.nextDouble() * (max - min);
        Location center = w.getWorldBorder().getCenter();
        int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
        int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
        if (!w.getWorldBorder().isInside(new Location(w, x, 64, z))) {
            trySpot(p, w, rank, min, max, left - 1);
            return;
        }
        w.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> Bukkit.getScheduler().runTask(host(), () -> {
            Location loc = findSafe(w, x, z);
            if (loc == null) {
                trySpot(p, w, rank, min, max, left - 1);
            } else {
                finishRtp(p, rank, loc);
            }
        })).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(host(), () -> trySpot(p, w, rank, min, max, left - 1));
            return null;
        });
    }

    private Location findSafe(World w, int x, int z) {
        if (w.getEnvironment() == World.Environment.NETHER) {
            for (int y = 120; y >= 33; y--) {
                if (safeSpot(w.getBlockAt(x, y - 1, z), w.getBlockAt(x, y, z), w.getBlockAt(x, y + 1, z))) {
                    return new Location(w, x + 0.5, y, z + 0.5);
                }
            }
            return null;
        }
        int y = w.getHighestBlockYAt(x, z) + 1;
        if (y <= w.getMinHeight() + 1 || y >= w.getMaxHeight() - 2) {
            return null;
        }
        if (safeSpot(w.getBlockAt(x, y - 1, z), w.getBlockAt(x, y, z), w.getBlockAt(x, y + 1, z))) {
            return new Location(w, x + 0.5, y, z + 0.5);
        }
        return null;
    }

    private boolean safeSpot(Block floor, Block feet, Block head) {
        Material f = floor.getType();
        if (!f.isSolid() || floor.isLiquid() || RTP_UNSAFE.contains(f) || f.name().endsWith("_LEAVES")) {
            return false;
        }
        return !feet.getType().isSolid() && !feet.isLiquid() && !RTP_UNSAFE.contains(feet.getType())
                && !head.getType().isSolid() && !head.isLiquid() && !RTP_UNSAFE.contains(head.getType());
    }

    private void finishRtp(Player p, String rank, Location loc) {
        long cooldown = getConfig().getLong("rtp.ranks." + rank + ".cooldown", 360);
        p.teleportAsync(loc).thenAccept(ok -> Bukkit.getScheduler().runTask(host(), () -> {
            if (!ok) {
                tell(p, "&cTeleport fehlgeschlagen.");
                return;
            }
            rtpCooldowns.put(p.getUniqueId(), System.currentTimeMillis() + cooldown * 1000L);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1, 1);
            p.spawnParticle(Particle.PORTAL, p.getLocation(), 24);
            tell(p, "&fZufällig teleportiert. &8(Cooldown: " + formatSeconds(cooldown) + ")");
        }));
    }

    @EventHandler public void onRtpQuit(PlayerQuitEvent e) {
        stopRtp(e.getPlayer().getUniqueId());
    }
    @EventHandler public void onPortal(PlayerPortalEvent e) {
        if (e.getPlayer().hasPermission("neptun.bypass.dimension")) return;
        if (e.getTo()==null) return;
        World.Environment env=e.getTo().getWorld().getEnvironment();
        if (env==World.Environment.NETHER && getConfig().getBoolean("dimensions.disable-nether")) { e.setCancelled(true); tell(e.getPlayer(),"&cDer Nether ist deaktiviert."); }
        if (env==World.Environment.THE_END && getConfig().getBoolean("dimensions.disable-end")) { e.setCancelled(true); tell(e.getPlayer(),"&cDas End ist deaktiviert."); }
    }
    private void loadHomes() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        var cfg = new org.bukkit.configuration.file.YamlConfiguration();
        try { cfg.load(new java.io.File(getDataFolder(),"homes.yml")); }
        catch (Exception ignored) { }
        for(String uuid : cfg.getKeys(false)) {
            Map<String,Location> map=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            var sec=cfg.getConfigurationSection(uuid);
            if(sec==null) continue;
            for(String name:sec.getKeys(false)) {
                Location loc=cfg.getLocation(uuid+"."+name);
                if(loc!=null) map.put(name,loc);
            }
            homes.put(UUID.fromString(uuid),map);
        }
    }
    private void saveHomes() {
        var cfg = new org.bukkit.configuration.file.YamlConfiguration();
        for(var entry:homes.entrySet()) for(var home:entry.getValue().entrySet())
            cfg.set(entry.getKey()+"."+home.getKey(),home.getValue());
        try { cfg.save(new java.io.File(getDataFolder(),"homes.yml")); }
        catch(Exception e) { getLogger().warning("Could not save homes: "+e.getMessage()); }
    }
}
