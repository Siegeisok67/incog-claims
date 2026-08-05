package com.incogdev.incogclaims.gui;

import com.incogdev.incogclaims.data.Claim;
import com.incogdev.incogclaims.data.PlayerData;
import com.incogdev.incogclaims.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;

public class ClaimMenuGUI implements InventoryHolder {

    public static final int INFO_SLOT = 10;
    public static final int EXPAND_SLOT = 12;
    public static final int TRUST_SLOT = 14;
    public static final int DELETE_SLOT = 16;

    private final Inventory inventory;
    private final Claim claim;

    public ClaimMenuGUI(Claim claim, PlayerData ownerData) {
        this.claim = claim;
        this.inventory = Bukkit.createInventory(this, 27, ChatColor.translateAlternateColorCodes('&', "&8Claim Menu"));
        build(ownerData);
    }

    private void build(PlayerData ownerData) {
        List<String> infoLore = new ArrayList<>();
        infoLore.add("&7Type: " + (claim.getType().name().equals("PVP") ? "&c&lPVP" : "&a&lPeaceful"));
        infoLore.add("&7Size: &f" + claim.getSize() + "x" + claim.getSize() + "x" + claim.getSize());
        infoLore.add("&7Trusted members: &f" + claim.getTrusted().size());
        infoLore.add("&7Claim blocks: &f" + ownerData.getClaimBlocks());
        inventory.setItem(INFO_SLOT, new ItemBuilder(Material.BOOK)
                .name("&5&lClaim Info")
                .lore(infoLore.toArray(new String[0]))
                .build());

        inventory.setItem(EXPAND_SLOT, new ItemBuilder(Material.EMERALD)
                .name("&a&lExpand / Resize Claim")
                .lore("&7Spend claim blocks to", "&7grow your claim.", "", "&eClick to open")
                .build());

        inventory.setItem(TRUST_SLOT, new ItemBuilder(Material.PLAYER_HEAD)
                .name("&b&lManage Trusted Players")
                .lore("&7Use &f/iclaims trust <player>", "&7or &f/iclaims untrust <player>")
                .build());

        inventory.setItem(DELETE_SLOT, new ItemBuilder(Material.TNT)
                .name("&c&lDelete Claim")
                .lore("&7Use &f/iclaims delete", "&7to confirm deletion.")
                .build());
    }

    public static void open(Player player, Claim claim, PlayerData ownerData) {
        ClaimMenuGUI gui = new ClaimMenuGUI(claim, ownerData);
        player.openInventory(gui.inventory);
    }

    public Claim getClaim() { return claim; }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
