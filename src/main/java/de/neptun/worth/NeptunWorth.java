package de.neptun.worth;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import de.neptun.core.Module;
import de.neptun.core.NeptunCore;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class NeptunWorth extends Module {
    public NeptunWorth(NeptunCore host) { super(host, "worth"); }
    @Override public String[] commands() { return new String[]{"worth", "sell", "shop", "neptunworth"}; }
    @Override public String legacyFolder() { return "NeptunWorth"; }
    @Override public void onReload() { reloadAll(); }

    private PriceService prices;
    private EconomyBridge economy;
    private Trading trading;
    private BrowserGui browser;
    private SellGui sellGui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        economy = new EconomyBridge();
        prices = new PriceService(this);
        prices.load();
        trading = new Trading(this);
        browser = new BrowserGui(this);
        sellGui = new SellGui(this);

        getServer().getPluginManager().registerEvents(browser, host());
        getServer().getPluginManager().registerEvents(sellGui, host());

        MainCommand handler = new MainCommand(this);
        for (String name : new String[]{"worth", "sell", "shop", "neptunworth"}) {
            PluginCommand pc = getCommand(name);
            if (pc != null) {
                pc.setExecutor(handler);
                pc.setTabCompleter(handler);
            }
        }
        getLogger().info("[worth] aktiv: " + Catalog.all().size() + " Items im Katalog. Vault(Money): "
                + economy.isAvailable(Currency.MONEY) + ", PlayerPoints(Neptuns): " + economy.isAvailable(Currency.NEPTUNS));
    }

    @Override
    public void onDisable() {
        // Offene Menüs schließen, damit Items aus dem Verkaufsmenü zurückgegeben werden
        for (Player p : Bukkit.getOnlinePlayers()) {
            Object holder = p.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof SellGui.Holder || holder instanceof BrowserGui.Holder) {
                p.closeInventory();
            }
        }
    }

    public void reloadAll() {
        reloadConfig();
        prices.load();
    }

    public PriceService prices() {
        return prices;
    }

    public EconomyBridge economy() {
        return economy;
    }

    public Trading trading() {
        return trading;
    }

    public BrowserGui browser() {
        return browser;
    }

    public SellGui sellGui() {
        return sellGui;
    }

    // ------------------------------------------------------------------ Hilfsfunktionen

    public String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    /** Nachricht aus der config.yml mit Prefix und {platzhaltern}. */
    public String msg(String key, String... kv) {
        return color(getConfig().getString("messages.prefix", "") + raw(key, kv));
    }

    public String raw(String key, String... kv) {
        String s = getConfig().getString("messages." + key, "&c[Nachricht fehlt: " + key + "]");
        for (int i = 0; i + 1 < kv.length; i += 2) {
            s = s.replace("{" + kv[i] + "}", kv[i + 1]);
        }
        return s;
    }

    public String cname(Currency c) {
        return c == Currency.MONEY
                ? getConfig().getString("currency.money-name", "Money")
                : getConfig().getString("currency.neptuns-name", "Neptuns");
    }

    public String num(double v) {
        return new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.GERMANY)).format(v);
    }

    public String fmt(Currency c, double v) {
        return num(v) + " " + cname(c);
    }

    /** Auszahlbarer Betrag: Money auf 2 Stellen, Neptuns abgerundet (ganze Zahlen). */
    public double payout(Currency c, double total) {
        if (c == Currency.MONEY) {
            return Math.round(total * 100.0) / 100.0;
        }
        return Math.floor(total + 1e-9);
    }
}
