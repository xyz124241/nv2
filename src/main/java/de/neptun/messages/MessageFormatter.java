package de.neptun.messages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class MessageFormatter {
    public static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').hexColors().build();
    private static final String DEFAULT_PREFIX =
            "<gradient:#00c6ff:#0072ff><bold>Neptune SMP</bold></gradient> <dark_gray>\u00bb</dark_gray> ";
    private static final String DEFAULT_SUBTITLE = "<gradient:#00c6ff:#0072ff>Neptune SMP</gradient>";

    private final NeptunMessages plugin;

    public MessageFormatter(NeptunMessages plugin) {
        this.plugin = plugin;
    }

    public Component body(Draft d) {
        Component parsed = d.legacy ? LEGACY.deserialize(d.text) : MM.deserialize(d.text);
        Component base = Component.empty().color(d.color);
        base = base.decoration(TextDecoration.BOLD, state(d.bold));
        base = base.decoration(TextDecoration.ITALIC, state(d.italic));
        base = base.decoration(TextDecoration.UNDERLINED, state(d.underlined));
        base = base.decoration(TextDecoration.STRIKETHROUGH, state(d.strikethrough));
        base = base.decoration(TextDecoration.OBFUSCATED, state(d.obfuscated));
        return base.append(parsed);
    }

    public Component prefix() {
        return MM.deserialize(plugin.getConfig().getString("prefix", DEFAULT_PREFIX));
    }

    public Component subtitle() {
        return MM.deserialize(plugin.getConfig().getString("title-subtitle", DEFAULT_SUBTITLE));
    }

    public Component full(Draft d) {
        Component body = body(d);
        if (!d.prefix) {
            return body;
        }
        return Component.empty().append(prefix()).append(body);
    }

    private static TextDecoration.State state(boolean on) {
        return on ? TextDecoration.State.TRUE : TextDecoration.State.NOT_SET;
    }
}
