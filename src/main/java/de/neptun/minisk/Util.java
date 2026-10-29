package de.neptun.minisk;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class Util {
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').hexColors().build();
    private static final Pattern ANGLE_HEX = Pattern.compile("<#([0-9a-fA-F]{6})>");
    private static final Pattern URL = Pattern.compile("https?://\\S+");

    private Util() {}

    /** Colors: &a &l &o ... , &#RRGGBB and <#RRGGBB> (all Minecraft color codes). */
    public static Component color(String s) {
        s = s.replace('\u00A7', '&');
        s = ANGLE_HEX.matcher(s).replaceAll("&#$1");
        return LEGACY.deserialize(s);
    }

    /** Colored text where every http(s) URL is clickable. */
    public static Component chat(String s) {
        return color(s).replaceText(TextReplacementConfig.builder()
                .match(URL)
                .replacement((m, b) -> b.clickEvent(ClickEvent.openUrl(m.group()))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to open"))))
                .build());
    }

    public static Component link(String label, String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;
        return color(label)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(Component.text(url)));
    }

    public static Component noItalic(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }

    public static String unquote(String s) {
        s = s.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) return s.substring(1, s.length() - 1);
        return s;
    }

    /** Splits on spaces but keeps "quoted text" together. */
    public static List<String> tokens(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean q = false, had = false;
        for (char c : s.toCharArray()) {
            if (c == '"') {
                q = !q;
                had = true;
            } else if (c == ' ' && !q) {
                if (cur.length() > 0 || had) out.add(cur.toString());
                cur.setLength(0);
                had = false;
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0 || had) out.add(cur.toString());
        return out;
    }

    /** Indentation based parser: returns the top-level nodes. */
    public static List<Node> parse(Path file) throws IOException {
        List<Node> roots = new ArrayList<>();
        List<Node> stack = new ArrayList<>();
        List<Integer> indents = new ArrayList<>();
        for (String raw : Files.readAllLines(file)) {
            String line = raw.replace("\t", "    ");
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int indent = line.length() - line.stripLeading().length();
            while (!indents.isEmpty() && indents.get(indents.size() - 1) >= indent) {
                indents.remove(indents.size() - 1);
                stack.remove(stack.size() - 1);
            }
            Node n = new Node(trimmed);
            if (stack.isEmpty()) roots.add(n);
            else stack.get(stack.size() - 1).kids.add(n);
            stack.add(n);
            indents.add(indent);
        }
        return roots;
    }
}
