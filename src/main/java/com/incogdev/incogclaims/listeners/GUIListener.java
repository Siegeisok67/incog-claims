package com.incogdev.incogclaims.listeners;

import com.incogdev.incogclaims.IncogClaims;
import com.incogdev.incogclaims.data.Claim;
import com.incogdev.incogclaims.data.ClaimType;
import com.incogdev.incogclaims.data.PlayerData;
import com.incogdev.incogclaims.gui.ClaimMenuGUI;
import com.incogdev.incogclaims.gui.ExpandGUI;
import com.incogdev.incogclaims.gui.TypeSelectGUI;
import com.incogdev.incogclaims.util.Msg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.concurrent.TimeUnit;

public class GUIListener implements Listener {

    private final IncogClaims plugin;

    public GUIListener(IncogClaims plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        var holder = event.getInventory().getHolder();
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (holder instanceof TypeSelectGUI) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

            ClaimType chosen = slot == TypeSelectGUI.PVP_SLOT ? ClaimType.PVP
                    : slot == TypeSelectGUI.PEACEFUL_SLOT ? ClaimType.PEACEFUL
                    : null;
            if (chosen == null) return; // clicked somewhere in the GUI that isn't a choice

            boolean firstChoice = !data.hasChosenType();

            if (!firstChoice) {
                // This menu can now be reopened via /iclaims select even after a choice
                // was already made, which makes picking here the same action as
                // /iclaims switch - so re-check the cooldown right here at click-time,
                // not just when the menu was opened. Re-checking live avoids a desync if
                // their cooldown state changed while the menu sat open (an admin clearing
                // it, or - the case that actually bit this plugin - the on-join delayed
                // task reopening this same GUI on top of a choice made moments earlier).
                long remaining = data.getSwitchCooldownRemainingMillis(plugin.getConfigManager().getSwitchCooldownDays());
                if (remaining > 0) {
                    long days = TimeUnit.MILLISECONDS.toDays(remaining);
                    long hours = TimeUnit.MILLISECONDS.toHours(remaining) % 24;
                    Msg.send(player, "&cYou can change your playstyle again in " + days + "d " + hours + "h.");
                    player.closeInventory();
                    return;
                }
                if (chosen == data.getType()) {
                    Msg.send(player, "&eYou're already " + chosen.name() + ".");
                    player.closeInventory();
                    return;
                }
            }

            plugin.applyTypeSelection(player, chosen);
            player.closeInventory();
            Msg.send(player, chosen == ClaimType.PVP
                    ? plugin.getConfigManager().getMessage("pvp-warning")
                    : plugin.getConfigManager().getMessage("peaceful-info"));
            return;
        }

        if (holder instanceof ClaimMenuGUI menuGui) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            Claim claim = menuGui.getClaim();

            if (slot == ClaimMenuGUI.EXPAND_SLOT) {
                if (!claim.getOwner().equals(player.getUniqueId()) && !player.hasPermission("incogclaims.admin")) {
                    Msg.send(player, "&cOnly the owner can expand this claim.");
                    return;
                }
                PlayerData ownerData = plugin.getPlayerDataManager().get(claim.getOwner());
                ExpandGUI.open(player, claim, plugin.getConfigManager().getClaimSizes(), ownerData.getClaimBlocks());
            } else if (slot == ClaimMenuGUI.TRUST_SLOT) {
                Msg.send(player, "&7Use &f/iclaims trust <player> &7or &f/iclaims untrust <player>");
            } else if (slot == ClaimMenuGUI.DELETE_SLOT) {
                Msg.send(player, "&7Use &f/iclaims delete &7to confirm deletion.");
            }
            return;
        }

        if (holder instanceof ExpandGUI expandGui) {
            event.setCancelled(true);
            Integer size = expandGui.getSizeForSlot(event.getRawSlot());
            if (size == null) return;

            Claim claim = expandGui.getClaim();
            if (!claim.getOwner().equals(player.getUniqueId()) && !player.hasPermission("incogclaims.admin")) {
                Msg.send(player, "&cOnly the owner can resize this claim.");
                return;
            }

            PlayerData ownerData = plugin.getPlayerDataManager().get(claim.getOwner());
            Integer cost = plugin.getConfigManager().getClaimSizes().get(size);
            if (cost == null) return;

            if (claim.getSize() == size) {
                Msg.send(player, "&eThat is already your current claim size.");
                return;
            }
            if (ownerData.getClaimBlocks() < cost) {
                Msg.send(player, "&cYou don't have enough claim blocks for that size.");
                return;
            }
            boolean overlaps = plugin.getClaimManager().wouldOverlap(claim, claim.getWorldName(),
                    claim.getCoreX(), claim.getCoreY(), claim.getCoreZ(), size);
            if (overlaps) {
                Msg.send(player, "&cResizing to that size would overlap another claim.");
                return;
            }

            ownerData.addClaimBlocks(-cost);
            plugin.getClaimManager().resizeClaim(claim, size);
            Msg.send(player, "&aClaim resized to &f" + size + "x" + size + "x" + size + "&a!");
            player.closeInventory();
        }
    }
}
