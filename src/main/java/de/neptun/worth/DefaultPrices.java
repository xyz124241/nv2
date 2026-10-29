package de.neptun.worth;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

/**
 * Automatisch erzeugte Standardpreise (Money pro Stück). Sie landen beim ersten Start in prices.yml
 * und können dort für jedes Item einzeln angepasst werden.
 */
final class DefaultPrices {

    private DefaultPrices() {
    }

    private static final Map<String, Double> E = new HashMap<>();
    private static final Map<String, Integer> PARTS = new HashMap<>();
    private static final Map<String, Double> ORE = new HashMap<>();
    private static final Map<String, Double> ENCH = new HashMap<>();

    private static void p(String name, double price) {
        E.put(name, price);
    }

    static {
        // Rohstoffe & Barren
        p("COAL", 15); p("CHARCOAL", 12); p("RAW_IRON", 25); p("IRON_INGOT", 40); p("IRON_NUGGET", 4.5);
        p("RAW_COPPER", 8); p("COPPER_INGOT", 12); p("RAW_GOLD", 60); p("GOLD_INGOT", 80); p("GOLD_NUGGET", 9);
        p("DIAMOND", 300); p("EMERALD", 200); p("LAPIS_LAZULI", 20); p("REDSTONE", 12); p("QUARTZ", 25);
        p("AMETHYST_SHARD", 20); p("NETHERITE_SCRAP", 600); p("NETHERITE_INGOT", 3000); p("ANCIENT_DEBRIS", 700);
        p("COAL_BLOCK", 135); p("IRON_BLOCK", 360); p("GOLD_BLOCK", 720); p("DIAMOND_BLOCK", 2700);
        p("EMERALD_BLOCK", 1800); p("LAPIS_BLOCK", 180); p("REDSTONE_BLOCK", 108); p("COPPER_BLOCK", 108);
        p("NETHERITE_BLOCK", 27000); p("QUARTZ_BLOCK", 100); p("RAW_IRON_BLOCK", 225); p("RAW_GOLD_BLOCK", 540);
        p("RAW_COPPER_BLOCK", 72); p("AMETHYST_BLOCK", 80);

        // Natur & einfache Blöcke
        p("DIRT", 1); p("GRASS_BLOCK", 2); p("COBBLESTONE", 1); p("STONE", 2); p("DEEPSLATE", 2);
        p("COBBLED_DEEPSLATE", 1); p("GRANITE", 1); p("DIORITE", 1); p("ANDESITE", 1); p("SAND", 2);
        p("RED_SAND", 2); p("GRAVEL", 1); p("CLAY_BALL", 4); p("CLAY", 16); p("OBSIDIAN", 40);
        p("CRYING_OBSIDIAN", 120); p("NETHERRACK", 1); p("GLOWSTONE_DUST", 12); p("GLOWSTONE", 48);
        p("SOUL_SAND", 4); p("END_STONE", 3); p("PURPUR_BLOCK", 8); p("SPONGE", 80); p("PRISMARINE_SHARD", 10);
        p("PRISMARINE_CRYSTALS", 15); p("STICK", 1); p("BAMBOO", 1); p("SUGAR_CANE", 3); p("CACTUS", 3);
        p("COBWEB", 20); p("SNOWBALL", 1); p("SNOW_BLOCK", 4); p("ICE", 4); p("PACKED_ICE", 12); p("BLUE_ICE", 50);
        p("MOSS_BLOCK", 3); p("GLASS", 3); p("TORCH", 2); p("LANTERN", 25); p("CHEST", 12); p("BARREL", 12);
        p("CRAFTING_TABLE", 8); p("FURNACE", 8); p("BLAST_FURNACE", 130); p("SMOKER", 50); p("ANVIL", 400);
        p("ENCHANTING_TABLE", 1500); p("BREWING_STAND", 150); p("CAULDRON", 280); p("BUCKET", 120);
        p("WATER_BUCKET", 125); p("LAVA_BUCKET", 160); p("MILK_BUCKET", 130); p("PAPER", 4); p("BOOK", 30);
        p("ENCHANTED_BOOK", 100); p("BOOKSHELF", 100); p("TNT", 60); p("HOPPER", 150); p("PISTON", 50);
        p("STICKY_PISTON", 75); p("OBSERVER", 60); p("DISPENSER", 40); p("DROPPER", 35); p("REPEATER", 30);
        p("COMPARATOR", 70); p("RAIL", 6); p("POWERED_RAIL", 90); p("MINECART", 120); p("SADDLE", 200);
        p("NAME_TAG", 150); p("LEAD", 40);

        // Essen
        p("BREAD", 10); p("APPLE", 8); p("GOLDEN_APPLE", 250); p("ENCHANTED_GOLDEN_APPLE", 5000);
        p("GOLDEN_CARROT", 60); p("COOKED_BEEF", 14); p("BEEF", 6); p("PORKCHOP", 6); p("COOKED_PORKCHOP", 14);
        p("CHICKEN", 5); p("COOKED_CHICKEN", 12); p("MUTTON", 5); p("COOKED_MUTTON", 12); p("RABBIT", 5);
        p("COOKED_RABBIT", 12); p("COD", 4); p("COOKED_COD", 10); p("SALMON", 5); p("COOKED_SALMON", 12);
        p("POTATO", 2); p("BAKED_POTATO", 6); p("CARROT", 2); p("BEETROOT", 2); p("WHEAT", 3); p("WHEAT_SEEDS", 1);
        p("MELON_SLICE", 1); p("MELON", 9); p("PUMPKIN", 6); p("CAKE", 100); p("COOKIE", 3); p("PUMPKIN_PIE", 40);
        p("SUGAR", 4); p("EGG", 3); p("HONEY_BOTTLE", 20); p("HONEYCOMB", 12); p("MUSHROOM_STEW", 20);
        p("SWEET_BERRIES", 2); p("GLOW_BERRIES", 4); p("DRIED_KELP", 2); p("ROTTEN_FLESH", 2);

        // Mob-Drops & Seltenes
        p("BONE", 6); p("STRING", 5); p("SPIDER_EYE", 8); p("GUNPOWDER", 15); p("ENDER_PEARL", 80);
        p("BLAZE_ROD", 100); p("BLAZE_POWDER", 50); p("GHAST_TEAR", 250); p("SLIME_BALL", 20); p("MAGMA_CREAM", 40);
        p("FEATHER", 3); p("LEATHER", 8); p("PHANTOM_MEMBRANE", 60); p("NAUTILUS_SHELL", 200);
        p("HEART_OF_THE_SEA", 3000); p("NETHER_STAR", 8000); p("DRAGON_EGG", 100000); p("DRAGON_HEAD", 20000);
        p("ELYTRA", 8000); p("TOTEM_OF_UNDYING", 4000); p("TRIDENT", 3000); p("SHULKER_SHELL", 400);
        p("END_CRYSTAL", 700); p("ENDER_EYE", 140); p("WITHER_SKELETON_SKULL", 3000); p("BEACON", 12000);
        p("CONDUIT", 15000); p("ARMADILLO_SCUTE", 80); p("TURTLE_SCUTE", 80); p("TURTLE_HELMET", 400);
        p("EXPERIENCE_BOTTLE", 120); p("MACE", 12000); p("HEAVY_CORE", 15000); p("BREEZE_ROD", 120);
        p("WIND_CHARGE", 30); p("TRIAL_KEY", 300); p("OMINOUS_TRIAL_KEY", 600); p("ECHO_SHARD", 400);
        p("DISC_FRAGMENT_5", 300); p("GOAT_HORN", 300); p("TOTEM", 4000); p("SHULKER_BOX", 1000);
        p("NETHERITE_UPGRADE_SMITHING_TEMPLATE", 2000); p("ENDER_CHEST", 900); p("ELDER_GUARDIAN_SPAWN_EGG", 800);
        p("IRON_DOOR", 80); p("SHIELD", 60); p("BOW", 60); p("CROSSBOW", 120); p("ARROW", 3);
        p("SPECTRAL_ARROW", 12); p("FISHING_ROD", 40); p("SHEARS", 80); p("FLINT_AND_STEEL", 45); p("FLINT", 3);
        p("COMPASS", 480); p("CLOCK", 960); p("MAP", 500); p("RECOVERY_COMPASS", 5000);

        // Werkzeug-/Rüstungsteile (Anzahl Material pro Item)
        PARTS.put("SWORD", 2); PARTS.put("PICKAXE", 3); PARTS.put("AXE", 3); PARTS.put("SHOVEL", 1);
        PARTS.put("HOE", 2); PARTS.put("HELMET", 5); PARTS.put("CHESTPLATE", 8); PARTS.put("LEGGINGS", 7);
        PARTS.put("BOOTS", 4); PARTS.put("HORSE_ARMOR", 6); PARTS.put("SPEAR", 2);

        // Erze
        ORE.put("COAL", 20.0); ORE.put("COPPER", 25.0); ORE.put("IRON", 45.0); ORE.put("GOLD", 90.0);
        ORE.put("REDSTONE", 60.0); ORE.put("LAPIS", 120.0); ORE.put("DIAMOND", 320.0); ORE.put("EMERALD", 220.0);
        ORE.put("NETHER_GOLD", 60.0); ORE.put("NETHER_QUARTZ", 40.0);

        // Enchants: Money pro Level
        ENCH.put("mending", 5000.0); ENCH.put("silk_touch", 3000.0); ENCH.put("fortune", 1500.0);
        ENCH.put("looting", 1000.0); ENCH.put("efficiency", 600.0); ENCH.put("unbreaking", 800.0);
        ENCH.put("sharpness", 700.0); ENCH.put("smite", 400.0); ENCH.put("bane_of_arthropods", 300.0);
        ENCH.put("power", 700.0); ENCH.put("protection", 700.0); ENCH.put("fire_protection", 450.0);
        ENCH.put("blast_protection", 450.0); ENCH.put("projectile_protection", 450.0);
        ENCH.put("feather_falling", 450.0); ENCH.put("respiration", 400.0); ENCH.put("aqua_affinity", 600.0);
        ENCH.put("thorns", 500.0); ENCH.put("depth_strider", 500.0); ENCH.put("frost_walker", 800.0);
        ENCH.put("soul_speed", 800.0); ENCH.put("swift_sneak", 1500.0); ENCH.put("riptide", 1500.0);
        ENCH.put("loyalty", 600.0); ENCH.put("channeling", 800.0); ENCH.put("impaling", 500.0);
        ENCH.put("luck_of_the_sea", 900.0); ENCH.put("lure", 700.0); ENCH.put("multishot", 1200.0);
        ENCH.put("piercing", 600.0); ENCH.put("quick_charge", 700.0); ENCH.put("punch", 400.0);
        ENCH.put("flame", 700.0); ENCH.put("infinity", 1500.0); ENCH.put("knockback", 400.0);
        ENCH.put("fire_aspect", 700.0); ENCH.put("sweeping_edge", 500.0); ENCH.put("density", 600.0);
        ENCH.put("breach", 900.0); ENCH.put("wind_burst", 1500.0); ENCH.put("binding_curse", 1.0);
        ENCH.put("vanishing_curse", 1.0);
    }

    /** Standard-Enchantpreis (Money pro Level). */
    static double enchant(String id) {
        Double v = ENCH.get(id);
        if (v != null) {
            return v;
        }
        return id.contains("curse") ? 1.0 : 1000.0;
    }

    private static double tool(String n) {
        String[] tiers = {"WOODEN_", "STONE_", "LEATHER_", "COPPER_", "IRON_", "CHAINMAIL_", "GOLDEN_", "DIAMOND_",
                "NETHERITE_", "TURTLE_"};
        double[] val = {4, 1, 8, 12, 40, 30, 80, 300, 300, 60};
        for (int i = 0; i < tiers.length; i++) {
            if (n.startsWith(tiers[i])) {
                Integer count = PARTS.get(n.substring(tiers[i].length()));
                if (count == null) {
                    return -1;
                }
                double v = val[i] * count;
                if (i == 8) {
                    v += 3500; // Netherite-Barren + Upgrade
                }
                return v;
            }
        }
        return -1;
    }

    static double money(Material m) {
        String n = m.name();
        Double e = E.get(n);
        if (e != null) {
            return e;
        }
        double t = tool(n);
        if (t >= 0) {
            return t;
        }
        if (n.endsWith("_SPAWN_EGG")) return 500;
        if (n.startsWith("MUSIC_DISC_")) return 1500;
        if (n.endsWith("_SMITHING_TEMPLATE")) return 400;
        if (n.endsWith("_POTTERY_SHERD")) return 150;
        if (n.endsWith("_BANNER_PATTERN")) return 200;
        if (n.endsWith("_ORE")) {
            boolean deep = n.startsWith("DEEPSLATE_");
            String core = n.replace("DEEPSLATE_", "").replace("_ORE", "");
            return ORE.getOrDefault(core, 20.0) + (deep ? 3 : 0);
        }
        if (n.endsWith("_LOG") || n.endsWith("_STEM") || n.endsWith("_WOOD") || n.endsWith("_HYPHAE")) return 6;
        if (n.endsWith("_PLANKS")) return 2;
        if (n.endsWith("_SAPLING")) return 4;
        if (n.endsWith("_LEAVES")) return 1;
        if (n.endsWith("_STAIRS")) return 3;
        if (n.endsWith("_SLAB")) return 1.5;
        if (n.endsWith("_FENCE_GATE")) return 6;
        if (n.endsWith("_FENCE")) return 3;
        if (n.endsWith("_TRAPDOOR")) return 8;
        if (n.endsWith("_DOOR")) return 6;
        if (n.endsWith("_PRESSURE_PLATE")) return 5;
        if (n.endsWith("_BUTTON")) return 2;
        if (n.endsWith("_HANGING_SIGN")) return 20;
        if (n.endsWith("_SIGN")) return 8;
        if (n.endsWith("_CHEST_BOAT") || n.endsWith("_CHEST_RAFT")) return 25;
        if (n.endsWith("_BOAT") || n.endsWith("_RAFT")) return 15;
        if (n.endsWith("_WALL")) return 3;
        if (n.endsWith("_WOOL")) return 6;
        if (n.endsWith("_CARPET")) return 3;
        if (n.endsWith("_BED")) return 40;
        if (n.endsWith("_BANNER")) return 20;
        if (n.endsWith("_CONCRETE_POWDER")) return 3;
        if (n.endsWith("_CONCRETE")) return 3;
        if (n.endsWith("_GLAZED_TERRACOTTA")) return 8;
        if (n.endsWith("_TERRACOTTA")) return 5;
        if (n.endsWith("_STAINED_GLASS_PANE")) return 1;
        if (n.endsWith("_STAINED_GLASS")) return 4;
        if (n.endsWith("_SHULKER_BOX")) return 1000;
        if (n.endsWith("_CANDLE")) return 10;
        if (n.endsWith("_DYE")) return 4;
        if (n.endsWith("_CORAL") || n.endsWith("_CORAL_FAN") || n.endsWith("_CORAL_BLOCK")) return 8;
        if (n.endsWith("_BRICKS")) return 4;
        if (n.endsWith("_HEAD") || n.endsWith("_SKULL")) return 500;
        if (n.endsWith("_BLOCK")) return 5;
        return m.isBlock() ? 3 : 5;
    }
}
