package de.neptun.messages;

import net.kyori.adventure.text.format.TextColor;

/** Entwurf einer Nachricht (pro Admin gespeichert). */
public final class Draft {
    public String text = "";
    public boolean legacy = false;
    public TextColor color = null;
    public boolean bold;
    public boolean italic;
    public boolean underlined;
    public boolean strikethrough;
    public boolean obfuscated;
    public boolean prefix = true;
    public boolean sound = true;
    public Display display = Display.CHAT;

    public boolean hasText() {
        return text != null && !text.isBlank();
    }

    public void resetStyle() {
        color = null;
        bold = false;
        italic = false;
        underlined = false;
        strikethrough = false;
        obfuscated = false;
    }

    public enum Display {
        CHAT("Chat"),
        TITLE("Titel"),
        ACTIONBAR("Actionbar");

        private final String label;

        Display(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public Display next() {
            Display[] all = values();
            return all[(ordinal() + 1) % all.length];
        }
    }
}
