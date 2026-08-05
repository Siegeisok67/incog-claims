package com.incogdev.incogclaims.listeners;

import com.incogdev.incogclaims.IncogClaims;
import com.incogdev.incogclaims.data.Claim;
import com.incogdev.incogclaims.data.ClaimType;
import com.incogdev.incogclaims.data.PlayerData;
import com.incogdev.incogclaims.gui.ClaimMenuGUI;
import com.incogdev.incogclaims.util.Msg;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ProtectionListener implements Listener {

    private final IncogClaims plugin;

    // Tracks which player primed a TNT block, keyed by block location, briefly, so we can
    // tag the resulting PrimedTnt entity with the responsible player's UUID.
    private final Map<String, UUID> pendingPrimers = new ConcurrentHashMap<>();

    public ProtectionListener(IncogClaims plugin) {
        this.plugin = plugin;
    }

    private boolean bypasses(Player player) {
        return plugin.getConfigManager().isOpBypass() && player.hasPermission("incogclaims.admin");
    }

    // ---------------------------------------------------------------
    // Block break
    // ---------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Claim claim = plugin.getClaimManager().getClaimAt(event.getBlock().getLocation());
        if (claim == null) return;

        boolean isCore = claim.isCoreBlock(event.getBlock().getX(), event.getBlock().getY(),
                event.getBlock().getZ(), event.getBlock().getWorld().getName());
        boolean isMember = claim.isMember(player.getUniqueId());
        boolean isBypass = bypasses(player);

        if (isCore) {
            boolean isRaidBreak = !isMember && !isBypass && claim.getType() == ClaimType.PVP
                    && hasClaimBreaker(player.getInventory().getItemInMainHand()) && isPvpPlayer(player);

            if (isMember || isBypass || isRaidBreak) {
                // Core destroyed -> the claim ceases to exist.
                plugin.getClaimManager().removeClaim(claim);
                if (isRaidBreak) {
                    Msg.send(player, "&c&lRAID SUCCESSFUL! &7You destroyed a claim's core.");
                } else {
                    Msg.send(player, "&eClaim core broken. Your claim has been deleted.");
                }
                return;
            }

            event.setCancelled(true);
            Msg.send(player, "&cYou cannot break another player's claim core.");
            return;
        }

        if (isMember || isBypass) return;

        if (claim.getType() == ClaimType.PEACEFUL) {
            event.setCancelled(true);
            Msg.send(player, "&cThis is a peaceful claim - it cannot be broken into.");
            return;
        }

        // PVP claim: normal hand-mining is still blocked unless the raider has the
        // Claim Breaker pickaxe. Raiding regular blocks is otherwise done via TNT.
        if (hasClaimBreaker(player.getInventory().getItemInMainHand()) && isPvpPlayer(player)) {
            return;
        }

        event.setCancelled(true);
        Msg.send(player, "&cYou don't have permission to break blocks in this claim. Try blowing it up.");
    }

    // ---------------------------------------------------------------
    // Block place
    // ---------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        ItemStack inHand = event.getItemInHand();
        ItemMeta meta = inHand.getItemMeta();
        boolean isCoreItem = meta != null && meta.getPersistentDataContainer()
                .has(plugin.getCoreBlockKey(), PersistentDataType.STRING);

        if (isCoreItem) {
            handleCorePlacement(event, player);
            return;
        }

        if (bypasses(player)) return;

        Claim claim = plugin.getClaimManager().getClaimAt(event.getBlockPlaced().getLocation());
        if (claim == null) return;
        if (claim.isMember(player.getUniqueId())) return;

        event.setCancelled(true);
        Msg.send(player, "&cYou can't place blocks in this claim.");
    }

    private void handleCorePlacement(BlockPlaceEvent event, Player player) {
        if (plugin.getClaimManager().hasClaim(player.getUniqueId())) {
            event.setCancelled(true);
            Msg.send(player, "&cYou already own a claim. Delete it first with /iclaims delete.");
            return;
        }

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (data.getType() == null) {
            event.setCancelled(true);
            Msg.send(player, "&cYou must choose Peaceful or PVP before placing a claim core.");
            return;
        }

        int size = plugin.getConfigManager().getSmallestSize();
        Location loc = event.getBlockPlaced().getLocation();
        boolean overlaps = plugin.getClaimManager().wouldOverlap(null, loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), size);
        if (overlaps) {
            event.setCancelled(true);
            Msg.send(player, "&cToo close to another claim.");
            return;
        }

        Claim claim = new Claim(UUID.randomUUID(), player.getUniqueId(), data.getType(), loc, size);
        plugin.getClaimManager().addClaim(claim);
        Msg.send(player, "&aClaim created! Size: " + size + "x" + size + "x" + size
                + ". Right-click the core block to manage it.");
    }

    // ---------------------------------------------------------------
    // Right-click core -> open claim menu
    // ---------------------------------------------------------------
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (block.getType() != plugin.getConfigManager().getClaimCoreMaterial()) return;

        Claim claim = plugin.getClaimManager().getClaimAt(block.getLocation());
        if (claim == null) return;
        if (!claim.isCoreBlock(block.getX(), block.getY(), block.getZ(), block.getWorld().getName())) return;

        Player player = event.getPlayer();
        if (!claim.isMember(player.getUniqueId()) && !bypasses(player)) return;

        event.setCancelled(true);
        PlayerData ownerData = plugin.getPlayerDataManager().get(claim.getOwner());
        ClaimMenuGUI.open(player, claim, ownerData);
    }

    // ---------------------------------------------------------------
    // Container access
    // ---------------------------------------------------------------
    @EventHandler
    public void onContainerOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (bypasses(player)) return;

        var holder = event.getInventory().getLocation();
        if (holder == null) return;

        Claim claim = plugin.getClaimManager().getClaimAt(holder);
        if (claim == null) return;
        if (claim.isMember(player.getUniqueId())) return;

        event.setCancelled(true);
        Msg.send(player, "&cYou don't have permission to open containers in this claim.");
    }

    // ---------------------------------------------------------------
    // PVP damage - blocked entirely inside peaceful claims
    // ---------------------------------------------------------------
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = resolvePlayerAttacker(event.getDamager());
        if (attacker == null) return;

        Claim victimClaim = plugin.getClaimManager().getClaimAt(victim.getLocation());
        Claim attackerClaim = plugin.getClaimManager().getClaimAt(attacker.getLocation());

        if ((victimClaim != null && victimClaim.getType() == ClaimType.PEACEFUL)
                || (attackerClaim != null && attackerClaim.getType() == ClaimType.PEACEFUL)) {
            event.setCancelled(true);
        }
    }

    private Player resolvePlayerAttacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    // ---------------------------------------------------------------
    // TNT raiding
    // ---------------------------------------------------------------
    @EventHandler
    public void onTntPrime(TNTPrimeEvent event) {
        if (event.getPrimingEntity() instanceof Player player) {
            pendingPrimers.put(locKey(event.getBlock().getLocation()), player.getUniqueId());
        }
    }

    @EventHandler
    public void onTntSpawn(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed tnt)) return;
        String key = locKey(event.getLocation());
        UUID primer = pendingPrimers.remove(key);
        if (primer != null) {
            tnt.setMetadata("incogclaims_primer", new org.bukkit.metadata.FixedMetadataValue(plugin, primer.toString()));
        }
    }

    private String locKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent event) {
        UUID primer = null;
        if (event.getEntity().hasMetadata("incogclaims_primer")) {
            try {
                primer = UUID.fromString(event.getEntity().getMetadata("incogclaims_primer").get(0).asString());
            } catch (Exception ignored) {}
        }

        boolean primerIsPvp = false;
        if (primer != null) {
            PlayerData primerData = plugin.getPlayerDataManager().get(primer);
            primerIsPvp = primerData.getType() == ClaimType.PVP;
        }

        List<Block> blocks = event.blockList();
        Iterator<Block> it = blocks.iterator();
        while (it.hasNext()) {
            Block b = it.next();
            Claim claim = plugin.getClaimManager().getClaimAt(b.getLocation());
            if (claim == null) continue;

            // The core block itself is always immune to explosions - it must be manually
            // broken (see onBreak) to actually delete a claim.
            if (claim.isCoreBlock(b.getX(), b.getY(), b.getZ(), b.getWorld().getName())) {
                it.remove();
                continue;
            }

            if (claim.getType() == ClaimType.PEACEFUL) {
                it.remove();
                continue;
            }

            // PVP claim
            if (!plugin.getConfigManager().isTntRaidEnabled()) {
                it.remove();
                continue;
            }
            if (plugin.getConfigManager().isRequirePvpAttackerToRaid() && !primerIsPvp) {
                it.remove();
            }
            // else: allowed to blow up - raid succeeds
        }
    }

    // ---------------------------------------------------------------
    // Claim Breaker pickaxe check
    // ---------------------------------------------------------------
    private boolean hasClaimBreaker(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(plugin.getClaimBreakerKey(), PersistentDataType.BYTE);
    }

    private boolean isPvpPlayer(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        return data.getType() == ClaimType.PVP;
    }
}
