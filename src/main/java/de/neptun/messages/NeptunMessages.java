package de.neptun.messages;

import de.neptun.core.Module;
import de.neptun.core.NeptunCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Op-Menü, um farbige Nachrichten als "Neptune SMP" in alle Welten zu senden (früher NeptuneMessages). Befehl: /nm */
public final class NeptunMessages extends Module {
    private final Map<UUID, Draft> drafts = new ConcurrentHashMap<>();
    private MessageFormatter formatter;
    private MessageSender sender;

    public NeptunMessages(NeptunCore host) {
        super(host, "messages");
    }

    @Override
    public String[] commands() {
        return new String[]{"nm"};
    }

    @Override
    public String legacyFolder() {
        return "NeptuneMessages";
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        formatter = new MessageFormatter(this);
        sender = new MessageSender(this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), host());
        getLogger().info("[messages] Neptune Messages aktiv. Benutzung: /nm");
    }

    public static boolean allowed(CommandSender s) {
        return s.isOp() || s.hasPermission("neptunemessages.use");
    }

    public Draft draft(UUID player) {
        return drafts.computeIfAbsent(player, id -> new Draft());
    }

    public MessageFormatter formatter() {
        return formatter;
    }

    public MessageSender sender() {
        return sender;
    }

    @Override
    public boolean onCommand(CommandSender s, Command command, String label, String[] args) {
        if (!allowed(s)) {
            s.sendMessage(Component.text("Daf\u00fcr hast du keine Berechtigung.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            s.sendMessage(Component.text("Neptune Messages: Config neu geladen.", NamedTextColor.GREEN));
            return true;
        }
        if (!(s instanceof Player player)) {
            s.sendMessage(Component.text("Das Men\u00fc gibt es nur im Spiel.", NamedTextColor.RED));
            return true;
        }
        Draft draft = draft(player.getUniqueId());
        if (args.length > 0) {
            draft.text = String.join(" ", args);
        }
        new NMMenu(this, draft).open(player);
        return true;
    }
}
