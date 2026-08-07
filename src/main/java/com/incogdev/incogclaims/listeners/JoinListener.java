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

        if (data.hasChosenType()) return;

        // Slight delay so the GUI opens cleanly after the player fully loads in. The
        // hasChosenType() check is deliberately re-done INSIDE the scheduled task, not
        // just before scheduling it - if the player manually runs /iclaims select and
        // picks a type during that ~1-second window, this must not fire and reopen the
        // picker on top of their already-made choice. It would otherwise get treated as
        // a playstyle *switch* and needlessly burn their switch cooldown the instant
        // they join, which is exactly the kind of join-delay/select desync to avoid.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            PlayerData current = plugin.getPlayerDataManager().get(player.getUniqueId());
            if (!current.hasChosenType()) {
                TypeSelectGUI.open(player);
            }
        }, 20L);
    }
}
