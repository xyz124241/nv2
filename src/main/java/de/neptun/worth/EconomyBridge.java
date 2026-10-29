package de.neptun.worth;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Spricht Vault (Money) und PlayerPoints (Neptuns) per Reflection an,
 * damit beim Bauen keine zusätzlichen Bibliotheken nötig sind.
 */
public final class EconomyBridge {

    // ---- Vault ----
    private Object vault;
    private Method vHas;
    private Method vDeposit;
    private Method vWithdraw;
    private Method vBalance;
    private Method vOk;

    @SuppressWarnings("unchecked")
    private boolean vault() {
        if (vault != null) {
            return true;
        }
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
                return false;
            }
            Class<Object> eco = (Class<Object>) Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<Object> rsp = Bukkit.getServicesManager().getRegistration(eco);
            if (rsp == null) {
                return false;
            }
            vHas = eco.getMethod("has", OfflinePlayer.class, double.class);
            vDeposit = eco.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            vWithdraw = eco.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            vBalance = eco.getMethod("getBalance", OfflinePlayer.class);
            vOk = Class.forName("net.milkbowl.vault.economy.EconomyResponse").getMethod("transactionSuccess");
            vault = rsp.getProvider();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ---- PlayerPoints ----
    private Object points() {
        try {
            Plugin pp = Bukkit.getPluginManager().getPlugin("PlayerPoints");
            if (pp == null || !pp.isEnabled()) {
                return null;
            }
            return pp.getClass().getMethod("getAPI").invoke(pp);
        } catch (Throwable t) {
            return null;
        }
    }

    public boolean isAvailable(Currency c) {
        return c == Currency.MONEY ? vault() : points() != null;
    }

    public double balance(OfflinePlayer p, Currency c) {
        try {
            if (c == Currency.MONEY) {
                return vault() ? (Double) vBalance.invoke(vault, p) : 0;
            }
            Object api = points();
            if (api == null) {
                return 0;
            }
            UUID id = p.getUniqueId();
            return (Integer) api.getClass().getMethod("look", UUID.class).invoke(api, id);
        } catch (Throwable t) {
            return 0;
        }
    }

    public boolean has(OfflinePlayer p, Currency c, double amount) {
        return balance(p, c) + 1e-9 >= amount;
    }

    public boolean deposit(OfflinePlayer p, Currency c, double amount) {
        try {
            if (c == Currency.MONEY) {
                if (!vault()) {
                    return false;
                }
                Object resp = vDeposit.invoke(vault, p, amount);
                return (Boolean) vOk.invoke(resp);
            }
            Object api = points();
            if (api == null) {
                return false;
            }
            return (Boolean) api.getClass().getMethod("give", UUID.class, int.class)
                    .invoke(api, p.getUniqueId(), (int) Math.round(amount));
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean withdraw(OfflinePlayer p, Currency c, double amount) {
        try {
            if (c == Currency.MONEY) {
                if (!vault()) {
                    return false;
                }
                Object resp = vWithdraw.invoke(vault, p, amount);
                return (Boolean) vOk.invoke(resp);
            }
            Object api = points();
            if (api == null) {
                return false;
            }
            return (Boolean) api.getClass().getMethod("take", UUID.class, int.class)
                    .invoke(api, p.getUniqueId(), (int) Math.round(amount));
        } catch (Throwable t) {
            return false;
        }
    }
}
