package de.neptun.minisk;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Menu {
    public String name;
    public String title = "Menu";
    public int rows = 3;
    public String fill;
    public final Map<Integer, Item> items = new HashMap<>();

    public static class Item {
        public String material = "STONE";
        public String name;
        public final List<String> lore = new ArrayList<>();
        public int amount = 1;
        public final List<Node> actions = new ArrayList<>();
    }

    public static class Holder implements InventoryHolder {
        public final Menu menu;

        public Holder(Menu menu) {
            this.menu = menu;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
