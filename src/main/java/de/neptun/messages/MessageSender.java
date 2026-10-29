package de.neptun.messages;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MessageSender {
    private final NeptunMessages plugin;

    public MessageSender(NeptunMessages plugin) {
        this.plugin = plugin;
    }

    public Result broadcast(Draft draft, String senderNameForLog) {
        Set<String> excluded = new HashSet<>();
        for (String name : plugin.getConfig().getStringList("excluded-worlds")) {
            excluded.add(name.toLowerCase(Locale.ROOT));
        }
        int players = 0;
        int worlds = 0;
        for (World world : Bukkit.getWorlds()) {
            if (excluded.contains(world.getName().toLowerCase(Locale.ROOT))) {
                continue;
            }
            List<Player> inWorld = world.getPlayers();
            if (!inWorld.isEmpty()) {
                worlds++;
            }
            for (Player target : inWorld) {
                deliver(target, draft, draft.sound);
                players++;
            }
        }
        if (plugin.getConfig().getBoolean("log-to-console", true)) {
            String plain = PlainTextComponentSerializer.plainText().serialize(plugin.formatter().full(draft));
            plugin.getLogger().info("[messages] [" + senderNameForLog + "] -> " + players + " Spieler in " + worlds + " Welten: " + plain);
        }
        return new Result(players, worlds);
    }

    public void preview(Player admin, Draft draft) {
        admin.sendMessage(Component.text("\u25bc Vorschau (nur f\u00fcr dich sichtbar)", NamedTextColor.GRAY));
        deliver(admin, draft, false);
    }

    private void deliver(Player target, Draft draft, boolean withSound) {
        MessageFormatter f = plugin.formatter();
        switch (draft.display) {
            case CHAT -> target.sendMessage(f.full(draft));
            case ACTIONBAR -> target.sendActionBar(f.full(draft));
            case TITLE -> {
                Component subtitle = draft.prefix ? f.subtitle() : Component.empty();
                Title.Times times = Title.Times.times(
                        ticks(plugin.getConfig().getInt("title-fade-in", 10)),
                        ticks(plugin.getConfig().getInt("title-stay", 80)),
                        ticks(plugin.getConfig().getInt("title-fade-out", 20)));
                target.showTitle(Title.title(f.body(draft), subtitle, times));
            }
        }
        if (withSound) {
            target.playSound(Sound.sound(Key.key("entity.experience_orb.pickup"), Sound.Source.MASTER, 1.0f, 1.0f));
        }
    }

    private static Duration ticks(int ticks) {
        return Duration.ofMillis(ticks * 50L);
    }

    public record Result(int players, int worlds) {
    }
}
