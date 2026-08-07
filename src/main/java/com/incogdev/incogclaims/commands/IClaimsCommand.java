package com.incogdev.incogclaims.commands;

import com.incogdev.incogclaims.IncogClaims;
import com.incogdev.incogclaims.data.Claim;
import com.incogdev.incogclaims.data.ClaimType;
import com.incogdev.incogclaims.data.PlayerData;
import com.incogdev.incogclaims.gui.ClaimMenuGUI;
import com.incogdev.incogclaims.gui.TypeSelectGUI;
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
            case "buy" -> handleBuy(sender);
            case "gui", "menu" -> handleGui(sender);
            case "showclaim" -> handleShowClaim(sender);
            case "select" -> handleSelect(sender);
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
        Msg.sendRaw(sender, "&7/iclaims core &f- get a claim core to place (30 min cooldown)");
        Msg.sendRaw(sender, "&7/iclaims buy &f- buy an additional claim core ("
                + plugin.getConfigManager().getExtraClaimCostBlocks() + " blocks + "
                + plugin.getConfigManager().getExtraClaimCostEndCrystals() + " end crystals)");
        Msg.sendRaw(sender, "&7/iclaims gui &f- open your claim menu");
        Msg.sendRaw(sender, "&7/iclaims trust <player> &f- allow a player to build/break");
        Msg.sendRaw(sender, "&7/iclaims untrust <player> &f- remove trust");
        Msg.sendRaw(sender, "&7/iclaims switch &f- switch PVP/Peaceful (3 day cooldown)");
        Msg.sendRaw(sender, "&7/iclaims info &f- view your claim's info");
        Msg.sendRaw(sender, "&7/iclaims delete &f- delete your claim");
        Msg.sendRaw(sender, "&7/iclaims showclaim &f- toggle a particle border around your claim(s)");
        Msg.sendRaw(sender, "&7/iclaims select &f- open the Peaceful/PVP playstyle menu (also works to change your choice later, same cooldown as /iclaims switch)");
        if (sender.isOp()) {
            Msg.sendRaw(sender, "&c/iclaims admin delete &f- delete the claim you're standing in");
            Msg.sendRaw(sender, "&c/iclaims admin give claimbreaker <player> &f- give the rare pickaxe");
            Msg.sendRaw(sender, "&c/iclaims admin give blocks <player> <amount> &f- give claim blocks");
            Msg.sendRaw(sender, "&c/iclaims admin removecooldown <player> &f- clear their switch cooldown");
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

        long cooldownMillis = TimeUnit.MINUTES.toMillis(plugin.getConfigManager().getCoreCooldownMinutes());
        long since = System.currentTimeMillis() - data.getLastCoreTimestamp();
        if (data.getLastCoreTimestamp() != 0 && since < cooldownMillis) {
            long remaining = cooldownMillis - since;
            long minutes = TimeUnit.MILLISECONDS.toMinutes(remaining);
            long seconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60;
            Msg.send(player, "&cYou can request another claim core in " + minutes + "m " + seconds + "s.");
            return;
        }

        data.setLastCoreTimestamp(System.currentTimeMillis());
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

    private void handleBuy(CommandSender sender) {
        if (!(sender instanceof Player player)) { Msg.sendRaw(sender, "&cPlayers only."); return; }

        if (!plugin.getClaimManager().hasClaim(player.getUniqueId())) {
            Msg.send(player, "&cGet your first claim core with /iclaims core before buying another.");
            return;
        }

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        int costBlocks = plugin.getConfigManager().getExtraClaimCostBlocks();
        int costCrystals = plugin.getConfigManager().getExtraClaimCostEndCrystals();

        if (data.getClaimBlocks() < costBlocks) {
            Msg.send(player, "&cYou need " + costBlocks + " claim blocks (you have " + data.getClaimBlocks() + ").");
            return;
        }
        int crystalsHeld = countMaterial(player, Material.END_CRYSTAL);
        if (crystalsHeld < costCrystals) {
            Msg.send(player, "&cYou need " + costCrystals + " end crystals (you have " + crystalsHeld + ").");
            return;
        }

        removeMaterial(player, Material.END_CRYSTAL, costCrystals);
        data.addClaimBlocks(-costBlocks);

        ItemStack core = buildExtraCoreItem();
        player.getInventory().addItem(core);
        Msg.send(player, "&aPurchased an additional claim core! Place it to create a new claim"
                + " (it starts at the smallest size - your other claim's size isn't carried over).");
    }

    private int countMaterial(Player player, Material material) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) count += stack.getAmount();
        }
        return count;
    }

    private void removeMaterial(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) continue;
            int take = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            remaining -= take;
            if (stack.getAmount() <= 0) player.getInventory().setItem(i, null);
        }
    }

    private ItemStack buildExtraCoreItem() {
        ItemStack item = new ItemBuilder(plugin.getConfigManager().getClaimCoreMaterial())
                .name("&5&lClaim Core &7(Extra)")
                .lore(
                        "&7Place this block to create",
                        "&7an additional claim, centered here.",
                        "",
                        "&7Starts at the smallest size -",
                        "&7does not inherit any expansions.",
                        "",
                        "&cBreaking this block later",
                        "&cwill delete this claim!"
                ).build();
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(plugin.getExtraCoreBlockKey(), PersistentDataType.STRING, "extra_core");
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Which of a player's (possibly several) claims a command like /iclaims trust or
     * /iclaims info should act on: the one they're currently standing in, if it's one of
     * theirs, otherwise their original/primary claim. Keeps single-claim players working
     * exactly as before while letting multi-claim owners manage an extra claim just by
     * standing in it.
     */
    private Claim resolveClaimForCommand(Player player) {
        Claim standing = plugin.getClaimManager().getClaimAt(player.getLocation());
        if (standing != null && standing.getOwner().equals(player.getUniqueId())) {
            return standing;
        }
        return plugin.getClaimManager().getClaimByOwner(player.getUniqueId());
    }

    private void handleGui(CommandSender sender) {
        if (!(sender instanceof Player player)) { Msg.sendRaw(sender, "&cPlayers only."); return; }
        Claim claim = resolveClaimForCommand(player);
        if (claim == null) {
            Msg.send(player, "&cYou don't own a claim yet. Use /iclaims core to get started.");
            return;
        }
        PlayerData ownerData = plugin.getPlayerDataManager().get(claim.getOwner());
        ClaimMenuGUI.open(player, claim, ownerData);
    }

    private void handleShowClaim(CommandSender sender) {
        if (!(sender instanceof Player player)) { Msg.sendRaw(sender, "&cPlayers only."); return; }
        boolean nowOn = plugin.getProtectionListener().toggleShowClaim(player.getUniqueId());
        Msg.send(player, nowOn
                ? "&aShowing claim borders. Toggle again with /iclaims showclaim to turn it off."
                : "&eClaim borders hidden.");
    }

    private void handleSelect(CommandSender sender) {
        if (!(sender instanceof Player player)) { Msg.sendRaw(sender, "&cPlayers only."); return; }
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        // If they've already picked, reopening this menu to change their mind is the same
        // action as /iclaims switch - so it has to respect the exact same cooldown, or
        // it'd just be a free way to dodge the switch-cooldown entirely.
        if (data.hasChosenType()) {
            long remaining = data.getSwitchCooldownRemainingMillis(plugin.getConfigManager().getSwitchCooldownDays());
            if (remaining > 0) {
                sendCooldownMessage(player, remaining);
                return;
            }
        }

        TypeSelectGUI.open(player);
    }

    private void sendCooldownMessage(Player player, long remainingMillis) {
        long days = TimeUnit.MILLISECONDS.toDays(remainingMillis);
        long hours = TimeUnit.MILLISECONDS.toHours(remainingMillis) % 24;
        Msg.send(player, "&cYou can change your playstyle again in " + days + "d " + hours + "h.");
    }

    private void handleTrust(CommandSender sender, String[] args, boolean trust) {
        if (!(sender instanceof Player player)) { Msg.sendRaw(sender, "&cPlayers only."); return; }
        if (args.length < 2) {
            Msg.send(player, "&cUsage: /iclaims " + (trust ? "trust" : "untrust") + " <player>");
            return;
        }
        Claim claim = resolveClaimForCommand(player);
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
            ClaimType targetType = plugin.getPlayerDataManager().get(target.getUniqueId()).getType();
            if (targetType == null) {
                Msg.send(player, "&cThat player hasn't chosen Peaceful or PVP yet, so they can't be trusted.");
                return;
            }
            if (targetType != claim.getType()) {
                if (claim.getType() == ClaimType.PEACEFUL) {
                    Msg.send(player, "&cYour claim is Peaceful - you can only trust other Peaceful players.");
                } else {
                    Msg.send(player, "&cYour claim is PVP - you can only trust other PVP players.");
                }
                return;
            }
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

        long remaining = data.getSwitchCooldownRemainingMillis(plugin.getConfigManager().getSwitchCooldownDays());
        if (remaining > 0) {
            sendCooldownMessage(player, remaining);
            return;
        }

        ClaimType newType = data.getType() == ClaimType.PVP ? ClaimType.PEACEFUL : ClaimType.PVP;
        plugin.applyTypeSelection(player, newType);

        Msg.send(player, "&aYou are now &f" + newType.name() + "&a.");
        if (newType == ClaimType.PVP) {
            Msg.send(player, plugin.getConfigManager().getMessage("pvp-warning"));
        }
    }

    private void handleInfo(CommandSender sender) {
        if (!(sender instanceof Player player)) { Msg.sendRaw(sender, "&cPlayers only."); return; }
        Claim claim = resolveClaimForCommand(player);
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        int totalClaims = plugin.getClaimManager().getClaimsOwnedBy(player.getUniqueId()).size();
        Msg.send(player, "&7Your type: &f" + (data.getType() == null ? "Not chosen" : data.getType().name()));
        Msg.send(player, "&7Claim blocks: &f" + data.getClaimBlocks());
        Msg.send(player, "&7Claims owned: &f" + totalClaims);
        if (claim == null) {
            Msg.send(player, "&7You don't own a claim.");
            return;
        }
        Msg.send(player, "&7Claim size: &f" + claim.getSize() + "x" + claim.getSize() + "x" + claim.getSize());
        Msg.send(player, "&7Trusted: &f" + claim.getTrusted().size());
        Msg.send(player, "&7Obsidian: &f" + claim.getObsidianCount() + "/" + plugin.getConfigManager().getMaxObsidianPerClaim());
    }

    private void handleDelete(CommandSender sender) {
        if (!(sender instanceof Player player)) { Msg.sendRaw(sender, "&cPlayers only."); return; }
        Claim claim = resolveClaimForCommand(player);
        if (claim == null) {
            Msg.send(player, "&cYou don't own a claim.");
            return;
        }
        plugin.getClaimManager().removeClaim(claim);
        Msg.send(player, claim.isPrimary() ? "&aClaim deleted." : "&aExtra claim deleted.");
    }

    private void handleAdmin(CommandSender sender, String[] args) {
        if (!sender.isOp()) {
            Msg.sendRaw(sender, "&cOnly server operators can use admin commands.");
            return;
        }
        if (args.length < 2) {
            Msg.sendRaw(sender, "&cUsage: /iclaims admin <delete|give|removecooldown> ...");
            return;
        }
        String action = args[1].toLowerCase();
        if (action.equals("delete")) {
            if (!(sender instanceof Player admin)) {
                Msg.sendRaw(sender, "&cYou must be an in-game player standing in the claim to delete it.");
                return;
            }
            Claim claim = plugin.getClaimManager().getClaimAt(admin.getLocation());
            if (claim == null) {
                Msg.sendRaw(sender, "&cYou're not standing inside a claim.");
                return;
            }
            plugin.getClaimManager().removeClaim(claim);
            Msg.sendRaw(sender, "&aDeleted the claim you were standing in.");
        } else if (action.equals("give")) {
            if (args.length < 4) {
                Msg.sendRaw(sender, "&cUsage: /iclaims admin give <claimbreaker|blocks> <player> [amount]");
                return;
            }
            String what = args[2].toLowerCase();
            if (what.equals("claimbreaker")) {
                Player target = Bukkit.getPlayer(args[3]);
                if (target == null) { Msg.sendRaw(sender, "&cPlayer not online."); return; }
                target.getInventory().addItem(EnchantListener.buildClaimBreakerPickaxe(plugin));
                Msg.sendRaw(sender, "&aGave " + target.getName() + " a Claim Breaker pickaxe.");
            } else if (what.equals("blocks")) {
                if (args.length < 5) { Msg.sendRaw(sender, "&cUsage: /iclaims admin give blocks <player> <amount>"); return; }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[3]);
                int amount;
                try {
                    amount = Integer.parseInt(args[4]);
                } catch (NumberFormatException e) {
                    Msg.sendRaw(sender, "&cAmount must be a number.");
                    return;
                }
                PlayerData data = plugin.getPlayerDataManager().get(target.getUniqueId());
                int cap = plugin.getConfigManager().getMaxClaimBlocks();
                data.setClaimBlocks(Math.max(0, Math.min(cap, data.getClaimBlocks() + amount)));
                Msg.sendRaw(sender, "&aGave " + args[3] + " " + amount + " claim blocks. (Now: " + data.getClaimBlocks() + ")");
                if (target.isOnline()) {
                    Msg.send((Player) target, "&aAn admin gave you &f" + amount + " &aclaim blocks.");
                }
            } else {
                Msg.sendRaw(sender, "&cUsage: /iclaims admin give <claimbreaker|blocks> <player> [amount]");
            }
        } else if (action.equals("removecooldown")) {
            if (args.length < 3) { Msg.sendRaw(sender, "&cUsage: /iclaims admin removecooldown <player>"); return; }
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) { Msg.sendRaw(sender, "&cThat player must be online."); return; }
            PlayerData data = plugin.getPlayerDataManager().get(target.getUniqueId());
            data.setLastSwitchTimestamp(0L);
            data.setLastCoreTimestamp(0L);
            Msg.sendRaw(sender, "&aCleared " + target.getName() + "'s switch and claim core cooldowns.");
            Msg.send(target, "&aAn admin cleared your PVP/Peaceful switch and claim core cooldowns.");
        } else {
            Msg.sendRaw(sender, "&cUsage: /iclaims admin <delete|give|removecooldown> ...");
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.isOp()) {
            Msg.sendRaw(sender, "&cOnly server operators can use admin commands.");
            return;
        }
        plugin.getConfigManager().load();
        Msg.sendRaw(sender, "&aIncog-Claims config reloaded.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> base = new ArrayList<>(List.of("help", "core", "buy", "gui", "trust", "untrust",
                "switch", "info", "delete", "showclaim", "select"));
        if (sender.isOp()) {
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
        if (args.length == 2 && args[0].equalsIgnoreCase("admin") && sender.isOp()) {
            return List.of("delete", "give", "removecooldown").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("give") && sender.isOp()) {
            return List.of("claimbreaker", "blocks").stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
