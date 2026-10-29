package de.neptun.worth;

import java.util.Locale;

public enum Currency {
    MONEY,
    NEPTUNS;

    public static Currency parse(String s) {
        if (s == null) {
            return null;
        }
        switch (s.toLowerCase(Locale.ROOT)) {
            case "money":
            case "geld":
                return MONEY;
            case "neptuns":
            case "neptun":
            case "points":
            case "playerpoints":
                return NEPTUNS;
            default:
                return null;
        }
    }

    public Currency other() {
        return this == MONEY ? NEPTUNS : MONEY;
    }

    public String arg() {
        return name().toLowerCase(Locale.ROOT);
    }
}
