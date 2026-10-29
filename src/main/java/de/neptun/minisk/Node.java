package de.neptun.minisk;

import java.util.ArrayList;
import java.util.List;

/** One line of a .sk script with its indented children. */
public class Node {
    public final String text;
    public final List<Node> kids = new ArrayList<>();

    public Node(String text) {
        this.text = text;
    }

    public String key() {
        int i = text.indexOf(':');
        return (i < 0 ? text : text.substring(0, i)).trim().toLowerCase();
    }

    public String value() {
        int i = text.indexOf(':');
        return i < 0 ? "" : Util.unquote(text.substring(i + 1).trim());
    }
}
