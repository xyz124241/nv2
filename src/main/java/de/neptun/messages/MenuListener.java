package de.neptun.messages;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MenuListener implements Listener {
    private enum Input { TEXT, HEX }

    private final NeptunMessages plugin;
    private final Map<UUID, Input> awaiting = new ConcurrentHashMap<>();

    public MenuListener(NeptunMessages plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof NMMenu menu)) {
            return;
        }
        event.setCancelled(true);
        HumanEntity who = event.getWhoClicked();
        if (!(who instanceof Player player)) {
            return;
        }
        if (!NeptunMessages.allowed(player)) {
            player.closeInventory();
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        handleClick(player, menu, event.getSlot());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof NMMenu) {
            event.setCancelled(true);
        }
    }

    private void handleClick(Player player, NMMenu menu, int slot) {
        Draft draft = menu.draft();
        switch (slot) {
            case NMMenu.SLOT_TEXT -> startInput(player, Input.TEXT);
            case NMMenu.SLOT_MODE -> { draft.legacy = !draft.legacy; menu.render(); }
            case NMMenu.SLOT_PREFIX -> { draft.prefix = !draft.prefix; menu.render(); }
            case NMMenu.SLOT_DISPLAY -> { draft.display = draft.display.next(); menu.render(); }
            case NMMenu.SLOT_SOUND -> { draft.sound = !draft.sound; menu.render(); }
            case NMMenu.SLOT_CLEAR -> { draft.text = ""; menu.render(); }
            case NMMenu.SLOT_BOLD -> { draft.bold = !draft.bold; menu.render(); }
            case NMMenu.SLOT_ITALIC -> { draft.italic = !draft.italic; menu.render(); }
            case NMMenu.SLOT_UNDERLINED -> { draft.underlined = !draft.underlined; menu.render(); }
            case NMMenu.SLOT_STRIKE -> { draft.strikethrough = !draft.strikethrough; menu.render(); }
            case NMMenu.SLOT_OBFUSCATED -> { draft.obfuscated = !draft.obfuscated; menu.render(); }
            case NMMenu.SLOT_RESET -> { draft.resetStyle(); menu.render(); }
            case NMMenu.SLOT_HEX -> startInput(player, Input.HEX);
            case NMMenu.SLOT_PREVIEW, NMMenu.SLOT_PREVIEW_BUTTON -> preview(player, draft);
            case NMMenu.SLOT_SEND -> send(player, draft);
            case NMMenu.SLOT_CLOSE -> player.closeInventory();
            default -> handleColorClick(menu, draft, slot);
        }
    }

    private void handleColorClick(NMMenu menu, Draft draft, int slot) {
        for (int i = 0; i < NMMenu.COLOR_SLOTS.length; i++) {
            if (NMMenu.COLOR_SLOTS[i] != slot) {
                continue;
            }
            TextColor clicked = NMMenu.COLORS.get(i).color();
            draft.color = clicked.equals(draft.color) ? null : clicked;
            menu.render();
            return;
        }
    }

    private void preview(Player player, Draft draft) {
        if (!draft.hasText()) {
            player.sendMessage(Component.text("Gib zuerst einen Text ein.", NamedTextColor.RED));
            return;
        }
        plugin.sender().preview(player, draft);
    }

    private void send(Player player, Draft draft) {
        if (!draft.hasText()) {
            player.sendMessage(Component.text("Gib zuerst einen Text ein.", NamedTextColor.RED));
            return;
        }
        player.closeInventory();
        MessageSender.Result result = plugin.sender().broadcast(draft, player.getName());
        player.sendMessage(Component.text("\u2714 Gesendet an " + result.players() + " Spieler in " + result.worlds() + " Welt(en).", NamedTextColor.GREEN));
    }

    private void startInput(Player player, Input type) {
        awaiting.put(player.getUniqueId(), type);
        player.closeInventory();
        if (type == Input.TEXT) {
            player.sendMessage(Component.text("Schreibe jetzt deinen Text in den Chat (MiniMessage oder &-Codes). Zum Abbrechen: abbrechen", NamedTextColor.AQUA));
        } else {
            player.sendMessage(Component.text("Schreibe jetzt eine Hex-Farbe in den Chat, z. B. #ff8800. Zum Abbrechen: abbrechen", NamedTextColor.AQUA));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!awaiting.containsKey(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String raw = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin.host(), () -> finishInput(player, raw));
    }

    private void finishInput(Player player, String raw) {
        Input type = awaiting.remove(player.getUniqueId());
        if (type == null || !player.isOnline() || !NeptunMessages.allowed(player)) {
            return;
        }
        Draft draft = plugin.draft(player.getUniqueId());
        if (raw.equalsIgnoreCase("abbrechen")) {
            player.sendMessage(Component.text("Abgebrochen.", NamedTextColor.GRAY));
        } else if (type == Input.TEXT) {
            draft.text = raw;
        } else {
            String hex = raw.startsWith("#") ? raw : "#" + raw;
            TextColor parsed = TextColor.fromHexString(hex);
            if (parsed == null) {
                player.sendMessage(Component.text("Das ist keine g\u00fcltige Hex-Farbe (Beispiel: #ff8800).", NamedTextColor.RED));
            } else {
                draft.color = parsed;
            }
        }
        if (draft.hasText()) {
            plugin.sender().preview(player, draft);
        }
        new NMMenu(plugin, draft).open(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        awaiting.remove(event.getPlayer().getUniqueId());
    }
}
