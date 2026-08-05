package com.incogdev.incogclaims.util;

import com.incogdev.incogclaims.IncogClaims;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class Msg {

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public static void send(CommandSender sender, String message) {
        String prefix = IncogClaims.getInstance().getConfigManager().getMessage("prefix");
        sender.sendMessage(color(prefix + message));
    }

    public static void sendRaw(CommandSender sender, String message) {
        sender.sendMessage(color(message));
    }

    /** Shows a short-lived action bar message (used for claim enter/leave notices). */
    public static void actionBar(Player player, String message) {
        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(message);
        player.sendActionBar(component);
    }
}
