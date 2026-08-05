package com.incogdev.incogclaims.commands;

import com.incogdev.incogclaims.IncogClaims;
import com.incogdev.incogclaims.data.Claim;
import com.incogdev.incogclaims.data.ClaimType;
import com.incogdev.incogclaims.data.PlayerData;
import com.incogdev.incogclaims.gui.ClaimMenuGUI;
import com.incogdev.incogclaims.listeners.EnchantListener;
import com.incogdev.incogclaims.util.ItemBuilder;
import com.incogdev.incogclaims.util.Msg;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class IClaimsCommand implements CommandExecutor, TabCompleter {

    private final IncogClaims plugin;

    public IClaimsCommand(IncogClaims plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "help" -> sendHelp(sender);
            case "core" -> handleCore(sender);
            case "gui", "menu" -> handleGui(sender);
            case "trust" -> handleTrust(sender, args, true);
            case "untrust" -> handleTrust(sender, args, false);
            case "expand" -> handleGui(sender); // expand is reached via the claim menu
            case "switch" -> handleSwitch(sender);
            case "info" -> handleInfo(sender);
            case "delete" -> handleDelete(sender);
            case "admin" -> handleAdmin(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        Msg.sendRaw(sender, "&8&m----------&r &5&lIncog-Claims v3.11 &8&m----------");
        Msg.sendRaw(sender, "&7/iclaims core &f- get a claim core to place");
        Msg.sendRaw(sender, "&7/iclaims gui &f- open your claim menu");
        Msg.sendRaw(sender, "&7/iclaims trust <player> &f- allow a player to build/break");
        Msg.sendRaw(sender, "&7/iclaims untrust <player> &f- remove trust");
        Msg.sendRaw(sender, "&7/iclaims switch &f- switch PVP/Peaceful (3 day cooldown)");
        Msg.sendRaw(sender, "&7/iclaims info &f- view your claim's info");
        Msg.sendRaw(sender, "&7/iclaims delete &f- delete your claim");
        if (sender.hasPermission("incogclaims.admin")) {
            Msg.sendRaw(sender, "&c/iclaims admin delete <player> &f- delete a player's claim");
            Msg.sendRaw(sender, "&c/iclaims admin give claimbreaker <player> &f- give the rare pickaxe");
            Msg.sendRaw(sender, "&c/iclaims reload &f- reload config.yml");
        }
        Msg.sendRaw(sender, "&8Made by &5Siegeisok67 &8and the &5Incog Dev Team");
    }

    private void handleCore(CommandSender sender) {
        if (!(sender instanceof Player player)) { Msg.sendRaw(sender, "&cPlayers only."); return; }
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (data.getType() == null) {
            Msg.send(player, "&cYou must choose Peaceful or PVP first.");
            return;
        }
        if (plugin.getClaimManager().hasClaim(player.getUniqueId())) {
            Msg.send(player, "&cYou already own a claim.");
            return;
        }
        ItemStack core = buildCoreItem();
        player.getInventory().addItem(core);
        Msg.send(player, "&aClaim core given! Place it to create your claim (centered on that block).");
    }

    private ItemStack buildCoreItem() {
        ItemStack item = new ItemBuilder(plugin.getConfigManager().getClaimCoreMaterial())
                .name("&5&lClaim Core")
                .lore(
                        "&7Place this block to create",
                        "&7your claim, centered here.",
                        "",
                        "&cBreaking this block later",
                        "&cwill delete the claim!"
                ).build();
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(plugin.getCoreBlockKey(), PersistentDataType.STRING, "core");
        item.setItemMeta(meta);
        return item;
    }

    private void handleGui(CommandSender sender) {
        if (!(sender instanceof Player player)) { Msg.sendRaw(sender, "&cPlayers only."); return; }
        Claim claim = plugin.getClaimManager().getClaimByOwner(player.getUniqueId());
        if (claim == null) {
            Msg.send(player, "&cYou don't own a claim yet. Use /iclaims core to get started.");
            return;
        }
        PlayerData ownerData = plugin.getPlayerDataManager().get(claim.getOwner());
        ClaimMenuGUI.open(player, claim, ownerData);
    }

    private void handleTrust(CommandSender sender, String[] args, boolean trust) {
        if (!(sender instanceof Player player)) { Msg.sendRaw(sender, "&cPlayers only."); return; }
        if (args.length < 2) {
            Msg.send(player, "&cUsage: /iclaims " + (trust ? "trust" : "untrust") + " <player>");
            return;
        }
        Claim claim = plugin.getClaimManager().getClaimByOwner(player.getUniqueId());
        if (claim == null) {
            Msg.send(player, "&cYou don't own a claim.");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getUniqueId().equals(player.getUniqueId())) {
            Msg.send(player, "&cYou can't trust yourself.");
            return;
        }
        if (trust) {
            claim.getTrusted().add(target.getUniqueId());
            Msg.send(player, "&aTrusted " + args[1] + ". They can now build/break in your claim.");
        } else {
            claim.getTrusted().remove(target.getUniqueId());
            Msg.send(player, "&eUntrusted " + args[1] + ".");
        }
    }

    private void handleSwitch(CommandSender sender) {
        if (!(sender instanceof Player player)) { Msg.sendRaw(sender, "&cPlayers only."); return; }
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (data.getType() == null) {
            Msg.send(player, "&cYou haven't chosen a type yet.");
            return;
        }

        long cooldownMillis = TimeUnit.DAYS.toMillis(plugin.getConfigManager().getSwitchCooldownDays());
        long since = System.currentTimeMillis() - data.getLastSwitchTimestamp();
        if (data.getLastSwitchTimestamp() != 0 && since < cooldownMillis) {
            long remaining = cooldownMillis - since;
            long days = TimeUnit.MILLISECONDS.toDays(remaining);
            long hours = TimeUnit.MILLISECONDS.toHours(remaining) % 24;
            Msg.send(player, "&cYou can switch again in " + days + "d " + hours + "h.");
            return;
        }

        ClaimType newType = data.getType() == ClaimType.PVP ? ClaimType.PEACEFUL : ClaimType.PVP;
        data.setType(newType);
        data.setLastSwitchTimestamp(System.currentTimeMillis());

        Claim claim = plugin.getClaimManager().getClaimByOwner(player.getUniqueId());
        if (claim != null) claim.setType(newType);

        Msg.send(player, "&aYou are now &f" + newType.name() + "&a.");
        if (newType == ClaimType.PVP) {
            Msg.send(player, plugin.getConfigManager().getMessage("pvp-warning"));
        }
    }

    private void handleInfo(CommandSender sender) {
        if (!(sender instanceof Player player)) { Msg.sendRaw(sender, "&cPlayers only."); return; }
        Claim claim = plugin.getClaimManager().getClaimByOwner(player.getUniqueId());
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        Msg.send(player, "&7Your type: &f" + (data.getType() == null ? "Not chosen" : data.getType().name()));
        Msg.send(player, "&7Claim blocks: &f" + data.getClaimBlocks());
        if (claim == null) {
            Msg.send(player, "&7You don't own a claim.");
            return;
        }
        Msg.send(player, "&7Claim size: &f" + claim.getSize() + "x" + claim.getSize() + "x" + claim.getSize());
        Msg.send(player, "&7Trusted: &f" + claim.getTrusted().size());
    }

    private void handleDelete(CommandSender sender) {
        if (!(sender instanceof Player player)) { Msg.sendRaw(sender, "&cPlayers only."); return; }
        Claim claim = plugin.getClaimManager().getClaimByOwner(player.getUniqueId());
        if (claim == null) {
            Msg.send(player, "&cYou don't own a claim.");
            return;
        }
        plugin.getClaimManager().removeClaim(claim);
        Msg.send(player, "&aClaim deleted.");
    }

    private void handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("incogclaims.admin")) {
            Msg.sendRaw(sender, "&cYou don't have permission.");
            return;
        }
        if (args.length < 2) {
            Msg.sendRaw(sender, "&cUsage: /iclaims admin <delete|give> ...");
            return;
        }
        String action = args[1].toLowerCase();
        if (action.equals("delete")) {
            if (args.length < 3) { Msg.sendRaw(sender, "&cUsage: /iclaims admin delete <player>"); return; }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
            Claim claim = plugin.getClaimManager().getClaimByOwner(target.getUniqueId());
            if (claim == null) { Msg.sendRaw(sender, "&cThat player has no claim."); return; }
            plugin.getClaimManager().removeClaim(claim);
            Msg.sendRaw(sender, "&aDeleted " + args[2] + "'s claim.");
        } else if (action.equals("give")) {
            if (args.length < 4 || !args[2].equalsIgnoreCase("claimbreaker")) {
                Msg.sendRaw(sender, "&cUsage: /iclaims admin give claimbreaker <player>");
                return;
            }
            Player target = Bukkit.getPlayer(args[3]);
            if (target == null) { Msg.sendRaw(sender, "&cPlayer not online."); return; }
            target.getInventory().addItem(EnchantListener.buildClaimBreakerPickaxe(plugin));
            Msg.sendRaw(sender, "&aGave " + target.getName() + " a Claim Breaker pickaxe.");
        } else {
            Msg.sendRaw(sender, "&cUsage: /iclaims admin <delete|give> ...");
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("incogclaims.admin")) {
            Msg.sendRaw(sender, "&cYou don't have permission.");
            return;
        }
        plugin.getConfigManager().load();
        Msg.sendRaw(sender, "&aIncog-Claims config reloaded.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> base = new ArrayList<>(List.of("help", "core", "gui", "trust", "untrust",
                "switch", "info", "delete"));
        if (sender.hasPermission("incogclaims.admin")) {
            base.add("admin");
            base.add("reload");
        }
        if (args.length == 1) {
            String p = args[0].toLowerCase();
            return base.stream().filter(s -> s.startsWith(p)).collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return List.of("delete", "give").stream().filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
