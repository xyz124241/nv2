package de.neptun.unknownkiller;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import de.neptun.core.Module;
import de.neptun.core.NeptunCore;
import org.bukkit.potion.PotionEffectType;

public final class NeptunUnknownKiller extends Module implements Listener {
    public NeptunUnknownKiller(NeptunCore host) { super(host, "unknownkiller"); }
    @Override public String[] commands() { return new String[]{}; }
    @Override public String legacyFolder() { return "NeptunUnknownKiller"; }
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, host());
        getLogger().info("NeptunUnknownKiller enabled.");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return;
        if (!getConfig().getBoolean("hide-killer-when-invisible", true)) return;

        boolean invisible = killer.hasPotionEffect(PotionEffectType.INVISIBILITY);
        if (!invisible) return;

        String replacement = getConfig().getString("replacement", "&kUnknown&r");
        if (replacement == null || replacement.isBlank()) replacement = "Unknown";
        String victimName = getConfig().getBoolean("hide-victim-name", false) ? "Unknown" : victim.getName();
        String formatted = getConfig().getString("pvp-message", "&8%victim% &7was eliminated by &8%killer%&7.");
        if (formatted == null || formatted.isBlank()) formatted = "&8%victim% &7was eliminated by &8%killer%&7.";
        formatted = formatted.replace("%victim%", victimName).replace("%killer%", replacement);
        Component message = LEGACY.deserialize(formatted);
        event.deathMessage(message);
    }
}
