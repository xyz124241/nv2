package de.neptun.worth;

import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Preise für Items inkl. Enchants, Tränke und Haltbarkeit. */
public final class PriceService {

    private final NeptunWorth plugin;
    private final File pricesFile;
    private final File enchantsFile;

    private final Map<Material, double[]> base = new EnumMap<>(Material.class);
    private final Map<String, double[]> ench = new HashMap<>();
    private final Set<Material> blacklist = EnumSet.noneOf(Material.class);

    private YamlConfiguration prices;
    private double ratio = 100;

    public PriceService(NeptunWorth plugin) {
        this.plugin = plugin;
        this.pricesFile = new File(plugin.getDataFolder(), "prices.yml");
        this.enchantsFile = new File(plugin.getDataFolder(), "enchants.yml");
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    public void load() {
        plugin.getDataFolder().mkdirs();
        blacklist.clear();
        for (String s : plugin.getConfig().getStringList("blacklist")) {
            Material m = Material.matchMaterial(s);
            if (m != null) {
                blacklist.add(m);
            }
        }
        ratio = Math.max(0.0001, plugin.getConfig().getDouble("neptuns-ratio", 100));

        // ---- prices.yml ----
        prices = YamlConfiguration.loadConfiguration(pricesFile);
        boolean dirty = false;
        base.clear();
        for (Material m : Catalog.all()) {
            if (blacklist.contains(m)) {
                continue;
            }
            String path = "prices." + m.name();
            double money;
            if (prices.isSet(path + ".money")) {
                money = prices.getDouble(path + ".money");
            } else {
                money = DefaultPrices.money(m);
                prices.set(path + ".money", money);
                dirty = true;
            }
            double nep;
            if (prices.isSet(path + ".neptuns")) {
                nep = prices.getDouble(path + ".neptuns");
            } else {
                nep = money <= 0 ? 0 : round2(Math.max(0.01, money / ratio));
                prices.set(path + ".neptuns", nep);
                dirty = true;
            }
            base.put(m, new double[]{money, nep});
        }
        if (dirty) {
            save(prices, pricesFile);
        }

        // ---- enchants.yml ----
        YamlConfiguration ey = YamlConfiguration.loadConfiguration(enchantsFile);
        boolean edirty = false;
        ench.clear();
        for (Enchantment e : Registry.ENCHANTMENT) {
            String id = enchantId(e);
            String path = "enchants." + id;
            double money;
            if (ey.isSet(path + ".money")) {
                money = ey.getDouble(path + ".money");
            } else {
                money = DefaultPrices.enchant(id);
                ey.set(path + ".money", money);
                edirty = true;
            }
            double nep;
            if (ey.isSet(path + ".neptuns")) {
                nep = ey.getDouble(path + ".neptuns");
            } else {
                nep = round2(money / ratio);
                ey.set(path + ".neptuns", nep);
                edirty = true;
            }
            ench.put(id, new double[]{money, nep});
        }
        if (edirty) {
            save(ey, enchantsFile);
        }
    }

    private void save(YamlConfiguration yml, File file) {
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Konnte " + file.getName() + " nicht speichern: " + ex.getMessage());
        }
    }

    private static String enchantId(Enchantment e) {
        org.bukkit.NamespacedKey k = e.getKey();
        return k.getNamespace().equals("minecraft") ? k.getKey() : k.getNamespace() + "_" + k.getKey();
    }

    // ------------------------------------------------------------------ Abfragen

    public boolean isBlacklisted(Material m) {
        return blacklist.contains(m);
    }

    /** Verkaufswert des unveränderten Items (1 Stück). 0 = nicht handelbar. */
    public double base(Material m, Currency c) {
        double[] v = base.get(m);
        return v == null ? 0 : v[c.ordinal()];
    }

    /** Kaufpreis für 1 Stück im Shop. */
    public double buyUnit(Material m, Currency c) {
        double b = base(m, c);
        if (b <= 0) {
            return 0;
        }
        return b * plugin.getConfig().getDouble("shop.buy-multiplier", 3.0);
    }

    public void setPrice(Material m, Currency c, double price) {
        double[] v = base.computeIfAbsent(m, k -> new double[]{0, 0});
        v[c.ordinal()] = price;
        blacklist.remove(m);
        prices.set("prices." + m.name() + "." + c.arg(), price);
        save(prices, pricesFile);
    }

    private double enchantPerLevel(Enchantment e, Currency c) {
        double[] v = ench.get(enchantId(e));
        if (v != null) {
            return v[c.ordinal()];
        }
        double money = DefaultPrices.enchant(enchantId(e));
        return c == Currency.MONEY ? money : round2(money / ratio);
    }

    private double potionBonus(PotionMeta pm, Currency c) {
        double money = 0;
        double potion = plugin.getConfig().getDouble("potion-bonus", 150);
        PotionType type = pm.getBasePotionType();
        if (type != null) {
            String key = type.getKey().getKey();
            boolean plain = key.equals("water") || key.equals("mundane") || key.equals("thick") || key.equals("awkward");
            if (!plain) {
                money += potion;
                if (key.startsWith("strong_") || key.startsWith("long_")) {
                    money += potion * 0.5;
                }
            }
        }
        for (PotionEffect pe : pm.getCustomEffects()) {
            money += 50.0 * (pe.getAmplifier() + 1) * Math.max(1.0, pe.getDuration() / 1200.0);
        }
        return c == Currency.MONEY ? money : money / ratio;
    }

    private double durabilityFactor(ItemStack item, ItemMeta meta) {
        if (!plugin.getConfig().getBoolean("sell.durability-scaling", true)) {
            return 1.0;
        }
        short max = item.getType().getMaxDurability();
        if (max <= 0 || !(meta instanceof Damageable)) {
            return 1.0;
        }
        int damage = ((Damageable) meta).getDamage();
        double frac = 1.0 - (damage / (double) max);
        double min = plugin.getConfig().getDouble("sell.min-durability-fraction", 0.25);
        return Math.max(min, Math.min(1.0, frac));
    }

    /** Verkaufswert für EIN Stück dieses konkreten Items (mit Enchants, Trank, Haltbarkeit). */
    public double unitPrice(ItemStack item, Currency c) {
        double b = base(item.getType(), c);
        if (b <= 0) {
            return 0;
        }
        double total = b;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return total;
        }
        for (Map.Entry<Enchantment, Integer> en : meta.getEnchants().entrySet()) {
            total += enchantPerLevel(en.getKey(), c) * en.getValue();
        }
        if (meta instanceof EnchantmentStorageMeta) {
            for (Map.Entry<Enchantment, Integer> en : ((EnchantmentStorageMeta) meta).getStoredEnchants().entrySet()) {
                total += enchantPerLevel(en.getKey(), c) * en.getValue();
            }
        }
        if (meta instanceof PotionMeta) {
            total += potionBonus((PotionMeta) meta, c);
        }
        return total * durabilityFactor(item, meta);
    }

    /** Aufschlüsselung für /worth hand (bereits eingefärbt). */
    public List<String> breakdown(ItemStack item, Currency c) {
        List<String> out = new ArrayList<>();
        double b = base(item.getType(), c);
        if (b <= 0) {
            out.add(plugin.color("&cDieses Item ist nicht verkäuflich."));
            return out;
        }
        out.add(plugin.color("&8&m      &r &bWert: &f" + item.getType().name() + " &8&m      "));
        out.add(plugin.color("&7Basis: &f" + plugin.fmt(c, b)));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            for (Map.Entry<Enchantment, Integer> en : meta.getEnchants().entrySet()) {
                out.add(plugin.color("&7+ " + enchantId(en.getKey()) + " " + en.getValue() + ": &f"
                        + plugin.fmt(c, enchantPerLevel(en.getKey(), c) * en.getValue())));
            }
            if (meta instanceof EnchantmentStorageMeta) {
                for (Map.Entry<Enchantment, Integer> en : ((EnchantmentStorageMeta) meta).getStoredEnchants().entrySet()) {
                    out.add(plugin.color("&7+ Buch " + enchantId(en.getKey()) + " " + en.getValue() + ": &f"
                            + plugin.fmt(c, enchantPerLevel(en.getKey(), c) * en.getValue())));
                }
            }
            if (meta instanceof PotionMeta) {
                double pb = potionBonus((PotionMeta) meta, c);
                if (pb > 0) {
                    out.add(plugin.color("&7+ Trank: &f" + plugin.fmt(c, pb)));
                }
            }
            double f = durabilityFactor(item, meta);
            if (f < 0.999) {
                out.add(plugin.color("&7Haltbarkeit: &f" + Math.round(f * 100) + "%"));
            }
        }
        double unit = unitPrice(item, c);
        out.add(plugin.color("&aPro Stück: &f" + plugin.fmt(c, unit)));
        if (item.getAmount() > 1) {
            out.add(plugin.color("&aFür " + item.getAmount() + " Stück: &f" + plugin.fmt(c, unit * item.getAmount())));
        }
        return out;
    }
}
