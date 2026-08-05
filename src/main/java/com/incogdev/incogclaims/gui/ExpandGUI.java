package com.incogdev.incogclaims.gui;

import com.incogdev.incogclaims.data.Claim;
import com.incogdev.incogclaims.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.LinkedHashMap;
import java.util.Map;

public class ExpandGUI implements InventoryHolder {

    private final Inventory inventory;
    private final Claim claim;
    // slot -> size
    private final Map<Integer, Integer> slotToSize = new LinkedHashMap<>();

    public ExpandGUI(Claim claim, Map<Integer, Integer> sizeCosts, int playerBlocks) {
        this.claim = claim;
        this.inventory = Bukkit.createInventory(this, 27, ChatColor.translateAlternateColorCodes('&', "&8Expand Claim"));
        build(sizeCosts, playerBlocks);
    }

    private void build(Map<Integer, Integer> sizeCosts, int playerBlocks) {
        int slot = 10;
        for (Map.Entry<Integer, Integer> entry : sizeCosts.entrySet()) {
            int size = entry.getKey();
            int cost = entry.getValue();
            boolean current = claim.getSize() == size;
            boolean affordable = playerBlocks >= cost;

            Material mat = current ? Material.LIME_CONCRETE : (affordable ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK);
            String status = current ? "&a&lCURRENT SIZE" : (affordable ? "&e&lClick to purchase" : "&c&lNot enough claim blocks");

            inventory.setItem(slot, new ItemBuilder(mat)
                    .name("&5&l" + size + "x" + size + "x" + size)
                    .lore(
                            "&7Cost: &f" + cost + " claim blocks",
                            "&7You have: &f" + playerBlocks,
                            "",
                            status
                    ).build());
            slotToSize.put(slot, size);
            slot += 2;
            if (slot > 16) break;
        }
    }

    public Integer getSizeForSlot(int slot) {
        return slotToSize.get(slot);
    }

    public Claim getClaim() { return claim; }

    public static void open(Player player, Claim claim, Map<Integer, Integer> sizeCosts, int playerBlocks) {
        ExpandGUI gui = new ExpandGUI(claim, sizeCosts, playerBlocks);
        player.openInventory(gui.inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
