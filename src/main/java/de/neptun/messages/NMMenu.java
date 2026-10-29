package de.neptun.messages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class NMMenu implements InventoryHolder {
    public static final int SLOT_PREVIEW = 4;
    public static final int SLOT_TEXT = 10;
    public static final int SLOT_MODE = 11;
    public static final int SLOT_PREFIX = 12;
    public static final int SLOT_DISPLAY = 13;
    public static final int SLOT_SOUND = 14;
    public static final int SLOT_CLEAR = 16;
    public static final int SLOT_BOLD = 19;
    public static final int SLOT_ITALIC = 20;
    public static final int SLOT_UNDERLINED = 21;
    public static final int SLOT_STRIKE = 22;
    public static final int SLOT_OBFUSCATED = 23;
    public static final int SLOT_RESET = 25;
    public static final int[] COLOR_SLOTS = {28, 29, 30, 31, 32, 33, 34, 35, 37, 38, 39, 40, 41, 42, 43, 44};
    public static final int SLOT_HEX = 46;
    public static final int SLOT_PREVIEW_BUTTON = 48;
    public static final int SLOT_SEND = 50;
    public static final int SLOT_CLOSE = 52;

    public record ColorEntry(String name, NamedTextColor color, Material material) {
    }

    public static final List<ColorEntry> COLORS = List.of(
            new ColorEntry("Schwarz", NamedTextColor.BLACK, Material.BLACK_WOOL),
            new ColorEntry("Dunkelblau", NamedTextColor.DARK_BLUE, Material.BLUE_WOOL),
            new ColorEntry("Dunkelgr\u00fcn", NamedTextColor.DARK_GREEN, Material.GREEN_WOOL),
            new ColorEntry("T\u00fcrkis", NamedTextColor.DARK_AQUA, Material.CYAN_WOOL),
            new ColorEntry("Dunkelrot", NamedTextColor.DARK_RED, Material.RED_WOOL),
            new ColorEntry("Lila", NamedTextColor.DARK_PURPLE, Material.PURPLE_WOOL),
            new ColorEntry("Gold", NamedTextColor.GOLD, Material.ORANGE_WOOL),
            new ColorEntry("Grau", NamedTextColor.GRAY, Material.LIGHT_GRAY_WOOL),
            new ColorEntry("Dunkelgrau", NamedTextColor.DARK_GRAY, Material.GRAY_WOOL),
            new ColorEntry("Blau", NamedTextColor.BLUE, Material.LIGHT_BLUE_WOOL),
            new ColorEntry("Gr\u00fcn", NamedTextColor.GREEN, Material.LIME_WOOL),
            new ColorEntry("Aqua", NamedTextColor.AQUA, Material.DIAMOND_BLOCK),
            new ColorEntry("Rot", NamedTextColor.RED, Material.REDSTONE_BLOCK),
            new ColorEntry("Pink", NamedTextColor.LIGHT_PURPLE, Material.MAGENTA_WOOL),
            new ColorEntry("Gelb", NamedTextColor.YELLOW, Material.YELLOW_WOOL),
            new ColorEntry("Wei\u00df", NamedTextColor.WHITE, Material.WHITE_WOOL));

    private final NeptunMessages plugin;
    private final Draft draft;
    private final Inventory inventory;

    public NMMenu(NeptunMessages plugin, Draft draft) {
        this.plugin = plugin;
        this.draft = draft;
        this.inventory = Bukkit.createInventory(this, 54, Component.text("Neptune Messages", NamedTextColor.DARK_AQUA));
        render();
    }

    public Draft draft() {
        return draft;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void render() {
        ItemStack filler = make(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), false);
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
        inventory.setItem(SLOT_PREVIEW, previewItem());
        inventory.setItem(SLOT_TEXT, make(Material.OAK_SIGN, t("<aqua><bold>Text eingeben"), false,
                t("<gray>Aktuell:"),
                draft.hasText()
                        ? Component.text(shorten(draft.text, 40), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                        : t("<dark_gray>(noch kein Text)"),
                Component.empty(),
                t("<yellow>Klick: <gray>Text im Chat schreiben")));
        inventory.setItem(SLOT_MODE, make(draft.legacy ? Material.WRITABLE_BOOK : Material.COMMAND_BLOCK,
                t("<aqua><bold>Format-Modus"), false,
                t(draft.legacy ? "<green>\u25b6 &-Farbcodes <gray>(&c, &l, &#ff8800)" : "<gray>  &-Farbcodes"),
                t(draft.legacy ? "<gray>  MiniMessage" : "<green>\u25b6 MiniMessage <gray>(<red>, <bold>, <gradient:...>)"),
                Component.empty(),
                t("<yellow>Klick: <gray>umschalten")));
        inventory.setItem(SLOT_PREFIX, make(Material.NAME_TAG, t("<aqua><bold>Neptune-SMP-Prefix: " + onOff(draft.prefix)), draft.prefix,
                t("<gray>Setzt \u201eNeptune SMP \u00bb\u201c vor die Nachricht."),
                t("<gray>Es steht nie ein Spielername dabei."),
                Component.empty(),
                t("<yellow>Klick: <gray>an/aus")));
        inventory.setItem(SLOT_DISPLAY, make(Material.ITEM_FRAME, t("<aqua><bold>Anzeige: <white>" + draft.display.label()), false,
                t("<gray>Chat, Titel (Mitte des Bildschirms)"),
                t("<gray>oder Actionbar (\u00fcber der Hotbar)."),
                Component.empty(),
                t("<yellow>Klick: <gray>weiterschalten")));
        inventory.setItem(SLOT_SOUND, make(Material.NOTE_BLOCK, t("<aqua><bold>Sound: " + onOff(draft.sound)), draft.sound,
                t("<gray>Kurzer Ton beim Empfangen."),
                Component.empty(),
                t("<yellow>Klick: <gray>an/aus")));
        inventory.setItem(SLOT_CLEAR, make(Material.BARRIER, t("<red><bold>Text l\u00f6schen"), false,
                t("<gray>L\u00f6scht nur den Text,"),
                t("<gray>Farbe und Stil bleiben.")));
        inventory.setItem(SLOT_BOLD, styleItem(Material.ANVIL, "Fett", TextDecoration.BOLD, draft.bold));
        inventory.setItem(SLOT_ITALIC, styleItem(Material.FEATHER, "Kursiv", TextDecoration.ITALIC, draft.italic));
        inventory.setItem(SLOT_UNDERLINED, styleItem(Material.STICK, "Unterstrichen", TextDecoration.UNDERLINED, draft.underlined));
        inventory.setItem(SLOT_STRIKE, styleItem(Material.SHEARS, "Durchgestrichen", TextDecoration.STRIKETHROUGH, draft.strikethrough));
        inventory.setItem(SLOT_OBFUSCATED, styleItem(Material.ENDER_EYE, "Magisch (zuf\u00e4llige Zeichen)", TextDecoration.OBFUSCATED, draft.obfuscated));
        inventory.setItem(SLOT_RESET, make(Material.MILK_BUCKET, t("<white><bold>Farbe & Stil zur\u00fccksetzen"), false,
                t("<gray>Entfernt Grundfarbe und alle Stile.")));
        for (int i = 0; i < COLORS.size(); i++) {
            ColorEntry entry = COLORS.get(i);
            boolean selected = entry.color().equals(draft.color);
            inventory.setItem(COLOR_SLOTS[i], make(entry.material(),
                    Component.text(entry.name(), entry.color()).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true),
                    selected,
                    t(selected ? "<green>\u2714 Ausgew\u00e4hlt <gray>(Klick: abw\u00e4hlen)" : "<yellow>Klick: <gray>als Grundfarbe")));
        }
        TextColor c = draft.color;
        boolean custom = c != null && COLORS.stream().noneMatch(e -> e.color().equals(c));
        inventory.setItem(SLOT_HEX, make(Material.PAINTING, t("<aqua><bold>Eigene Farbe (Hex)"), custom,
                t("<gray>Aktuell: <white>" + (c == null ? "keine" : c.asHexString())),
                Component.empty(),
                t("<yellow>Klick: <gray>z. B. #ff8800 im Chat eingeben")));
        inventory.setItem(SLOT_PREVIEW_BUTTON, make(Material.SPYGLASS, t("<aqua><bold>Vorschau im Chat"), false,
                t("<gray>Zeigt dir die Nachricht genau so,"),
                t("<gray>wie sie im Spiel aussehen w\u00fcrde."),
                t("<gray>Nur du siehst sie.")));
        inventory.setItem(SLOT_SEND, make(Material.LIME_CONCRETE, t("<green><bold>SENDEN"), true,
                t("<gray>Schickt die Nachricht an alle Spieler"),
                t("<gray>in <white>allen Welten<gray>."),
                t("<gray>Absender: <white>Neptune SMP"),
                Component.empty(),
                t("<yellow>Klick: <gray>jetzt senden")));
        inventory.setItem(SLOT_CLOSE, make(Material.RED_CONCRETE, t("<red><bold>Schlie\u00dfen"), false));
    }

    private ItemStack previewItem() {
        Component name = draft.hasText()
                ? Component.empty().decoration(TextDecoration.ITALIC, false).append(plugin.formatter().full(draft))
                : t("<dark_gray>(noch kein Text)");
        return make(Material.PAPER, name, false,
                t("<gray>\u25b2 So sieht die Nachricht im Spiel aus."),
                t("<gray>Anzeige: <white>" + draft.display.label()),
                Component.empty(),
                t("<yellow>Klick: <gray>Vorschau im Chat"));
    }

    private ItemStack styleItem(Material material, String label, TextDecoration decoration, boolean on) {
        Component name = Component.text(label, NamedTextColor.WHITE)
                .decoration(decoration, true)
                .decoration(TextDecoration.ITALIC, decoration == TextDecoration.ITALIC);
        return make(material, name, on, t(on ? "<green>\u2714 An" : "<red>\u2718 Aus"), t("<yellow>Klick: <gray>umschalten"));
    }

    private static String onOff(boolean on) {
        return on ? "<green>AN" : "<red>AUS";
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "\u2026";
    }

    private static Component t(String miniMessage) {
        return MessageFormatter.MM.deserialize(miniMessage).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack make(Material material, Component name, boolean glow, Component... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        if (lore.length > 0) {
            meta.lore(new ArrayList<>(List.of(lore)));
        }
        if (glow) {
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }
}
