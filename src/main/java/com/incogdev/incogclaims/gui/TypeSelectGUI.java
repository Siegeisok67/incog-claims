package com.incogdev.incogclaims.gui;

import com.incogdev.incogclaims.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class TypeSelectGUI implements InventoryHolder {

    public static final int PVP_SLOT = 2;
    public static final int PEACEFUL_SLOT = 6;

    private final Inventory inventory;

    public TypeSelectGUI() {
        this.inventory = org.bukkit.Bukkit.createInventory(this, 9, org.bukkit.ChatColor.translateAlternateColorCodes('&', "&8Choose Your Playstyle"));
        build();
    }

    private void build() {
        inventory.setItem(PVP_SLOT, new ItemBuilder(Material.NETHERITE_SWORD)
                .name("&c&lAggressive / PVP")
                .lore(
                        "&7You can fight and raid other",
                        "&7PVP players' claims.",
                        "",
                        "&c&lWARNING:",
                        "&7Other PVP players can raid",
                        "&7your claim by blowing up",
                        "&7its blocks with TNT!",
                        "",
                        "&eClick to select"
                ).build());

        inventory.setItem(PEACEFUL_SLOT, new ItemBuilder(Material.SHIELD)
                .name("&a&lPeaceful")
                .lore(
                        "&7No PVP, no fighting.",
                        "&7Your claim can never be",
                        "&7broken into or raided.",
                        "",
                        "&eClick to select"
                ).build());
    }

    public static void open(Player player) {
        TypeSelectGUI gui = new TypeSelectGUI();
        player.openInventory(gui.inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
