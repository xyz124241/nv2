package de.neptun.minisk;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import de.neptun.core.Module;
import de.neptun.core.NeptunCore;

import java.util.Map;

public class MiniSk extends Module implements Listener {
    private Engine engine;

    public MiniSk(NeptunCore host) { super(host, "minisk"); }
    @Override public String[] commands() { return new String[]{"minisk", "discord", "store"}; }
    @Override public String legacyFolder() { return "MiniSk"; }
    @Override public void onReload() { reloadConfig(); if (engine != null) engine.load(); }

    @Override
    public void onEnable() {
        saveResource("scripts/example.sk", false);
        saveDefaultConfig();
        engine = new Engine(this);
        getServer().getPluginManager().registerEvents(this, host());
        getLogger().info("Loaded " + engine.load() + " script entries.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String command = cmd.getName().toLowerCase();
        if (command.equals("discord") || command.equals("dc")) {
            String url = getConfig().getString("discord-url", "https://discord.gg/EaCjRVkEQu");
            if (url == null || url.isBlank()) {
                sender.sendMessage(Util.color("&cDer Discord-Link ist noch nicht konfiguriert."));
            } else {
                sender.sendMessage(Util.link(url, url));
            }
            return true;
        }
        if (command.equals("store")) {
            String message = getConfig().getString("store-message", "&eUnser Store:");
            if (message != null && !message.isBlank()) sender.sendMessage(Util.chat(message));
            for (Map<?, ?> entry : getConfig().getMapList("store-links")) {
                Object labelValue = entry.get("text");
                Object urlValue = entry.get("url");
                if (urlValue == null || urlValue.toString().isBlank()) continue;
                String url = urlValue.toString().trim();
                String linkText = labelValue == null || labelValue.toString().isBlank()
                        ? url : labelValue.toString();
                sender.sendMessage(Util.link(linkText, url));
            }
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(Util.color("&aMiniSk: loaded " + engine.load() + " entries."));
        } else {
            sender.sendMessage(Util.color("&e/minisk reload"));
        }
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu.Holder h)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        Menu.Item it = h.menu.items.get(e.getSlot());
        if (it == null || it.actions.isEmpty()) return;
        Player p = (Player) e.getWhoClicked();
        Bukkit.getScheduler().runTask(host(), () -> engine.run(it.actions, p, new String[0]));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu.Holder) e.setCancelled(true);
    }
}
