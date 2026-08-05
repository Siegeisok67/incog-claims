package com.incogdev.incogclaims.listeners;

import com.incogdev.incogclaims.IncogClaims;
import com.incogdev.incogclaims.data.PlayerData;
import com.incogdev.incogclaims.gui.TypeSelectGUI;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinListener implements Listener {

    private final IncogClaims plugin;

    public JoinListener(IncogClaims plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        if (!data.hasChosenType()) {
            // slight delay so the GUI opens cleanly after the player fully loads in
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    TypeSelectGUI.open(player);
                }
            }, 20L);
        }
    }
}
