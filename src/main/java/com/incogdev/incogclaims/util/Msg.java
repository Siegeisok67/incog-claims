package com.incogdev.incogclaims.util;

import com.incogdev.incogclaims.IncogClaims;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

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
}
