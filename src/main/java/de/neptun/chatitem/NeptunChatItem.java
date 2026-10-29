package de.neptun.chatitem;

import de.neptun.core.Module;
import de.neptun.core.NeptunCore;
import de.neptun.social.NeptunSocial;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * [i] im Chat: ersetzt den Platzhalter durch das Item in der Hand.
 * Name in Item-Farbe (Seltenheit / eigener Name), Hover = komplette Inventar-Beschreibung
 * (Name, Lore, Verzauberungen, ...). Optional werden die Verzauberungen direkt im Chat angezeigt.
 */
public final class NeptunChatItem extends Module implements Listener {

    private Pattern pattern;

    public NeptunChatItem(NeptunCore host) {
        super(host, "chatitem");
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        compile();
        getServer().getPluginManager().registerEvents(this, host());
        getLogger().info("[chatitem] aktiv. Platzhalter: " + getConfig().getStringList("tokens"));
    }

    @Override
    public void onReload() {
        reloadConfig();
        compile();
    }

    private void compile() {
        List<String> tokens = getConfig().getStringList("tokens");
        if (tokens.isEmpty()) {
            tokens = List.of("[i]", "[item]");
        }
        List<String> quoted = new ArrayList<>();
        for (String t : tokens) {
            if (!t.isBlank()) {
                quoted.add(Pattern.quote(t));
            }
        }
        pattern = Pattern.compile(String.join("|", quoted), Pattern.CASE_INSENSITIVE);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("neptun.chatitem")) {
            return;
        }
        Module social = host().module("social");
        if (social instanceof NeptunSocial ns && !ns.itemDisplay(player.getUniqueId())) {
            return;
        }
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (!pattern.matcher(plain).find()) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        Component replacement;
        if (hand == null || hand.getType().isAir()) {
            replacement = MiniMessage.miniMessage().deserialize(getConfig().getString("empty-hand-text", "<gray>[Leere Hand]"));
        } else {
            replacement = itemComponent(hand);
        }
        event.message(event.message().replaceText(
                TextReplacementConfig.builder().match(pattern).once().replacement(replacement).build()));
    }

    private Component itemComponent(ItemStack hand) {
        // displayName(): "[Name]" in der Farbe des Items (Seltenheit bzw. eigener Name/Farben),
        // Hover-Event = komplette Item-Beschreibung wie im Inventar.
        Component result = hand.displayName();
        if (getConfig().getBoolean("show-amount", true) && hand.getAmount() > 1) {
            result = result.append(Component.text(" x" + hand.getAmount(), NamedTextColor.GRAY).hoverEvent(hand));
        }
        if (getConfig().getBoolean("inline-enchants", false)) {
            Component ench = enchantList(hand);
            if (ench != null) {
                result = result.append(Component.text(" ")).append(ench.hoverEvent(hand));
            }
        }
        return result;
    }

    private Component enchantList(ItemStack hand) {
        Map<Enchantment, Integer> all = new LinkedHashMap<>(hand.getEnchantments());
        ItemMeta meta = hand.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storage) {
            all.putAll(storage.getStoredEnchants());
        }
        if (all.isEmpty()) {
            return null;
        }
        Component out = Component.text("(", NamedTextColor.DARK_GRAY);
        boolean first = true;
        for (Map.Entry<Enchantment, Integer> e : all.entrySet()) {
            if (!first) {
                out = out.append(Component.text(", ", NamedTextColor.DARK_GRAY));
            }
            first = false;
            String key = e.getKey().getKey().getNamespace() + "." + e.getKey().getKey().getKey();
            Component name = Component.translatable("enchantment." + key, NamedTextColor.AQUA);
            if (e.getKey().getMaxLevel() != 1 || e.getValue() != 1) {
                name = name.append(Component.text(" ")).append(Component.translatable("enchantment.level." + e.getValue(), NamedTextColor.AQUA));
            }
            out = out.append(name);
        }
        return out.append(Component.text(")", NamedTextColor.DARK_GRAY));
    }
}
