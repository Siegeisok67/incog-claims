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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ProtectionListener implements Listener {

    private final IncogClaims plugin;

    // Tracks which player primed a TNT block, keyed by block location, briefly, so we can
    // tag the resulting PrimedTnt entity with the responsible player's UUID.
    private final Map<String, UUID> pendingPrimers = new ConcurrentHashMap<>();

    // Tracks which claim (by ID) each online player is currently standing inside, so we
    // only send the "entered a claim" notice once per entry, not every tick.
    private final Map<UUID, UUID> currentClaim = new ConcurrentHashMap<>();

    // Guards against log/TPS spam: if onMove ever throws, we log it once instead of every tick.
    private volatile boolean loggedMoveError = false;

    // Players who've toggled /iclaims showclaim on - they see a particle border around
    // whichever one of their claims they're currently standing in, redrawn once a second
    // by tickBorders().
    private final Set<UUID> showingBorder = ConcurrentHashMap.newKeySet();

    public ProtectionListener(IncogClaims plugin) {
        this.plugin = plugin;
    }

    /** Toggles the claim-border particle display for this player. Returns the new state. */
    public boolean toggleShowClaim(UUID uuid) {
        if (showingBorder.remove(uuid)) return false;
        showingBorder.add(uuid);
        return true;
    }

    /**
     * Redraws a particle wireframe (green "happy villager" sparkles, same as trading with
     * villagers) around ONLY the claim the toggled-on player is currently standing inside
     * (and only if it's a claim they're actually a member of - owner or trusted). If
     * they're not standing in one of their own claims right now, nothing is drawn. This
     * intentionally does not scan/render every claim they own from a distance anymore -
     * it's a live "which claim am I in, and where's its edge" indicator, not a claim map.
     */
    public void tickBorders() {
        if (showingBorder.isEmpty()) return;

        for (UUID uuid : showingBorder) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null || !player.isOnline()) continue;

            Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation());
            if (claim == null || !claim.isMember(uuid)) continue;

            drawClaimBorder(player, claim);
        }
    }

    private void drawClaimBorder(Player player, Claim claim) {
        org.bukkit.World world = player.getWorld();
        int half = claim.getHalf();
        int minX = claim.getCoreX() - half, maxX = claim.getCoreX() + half;
        int minY = claim.getCoreY() - half, maxY = claim.getCoreY() + half;
        int minZ = claim.getCoreZ() - half, maxZ = claim.getCoreZ() + half;
        // Particle spacing along each edge - lower is denser/easier to see. Configurable
        // since only ever rendering ONE claim at a time (see above) gives us headroom to
        // default this a lot denser than before without spamming packets.
        int step = plugin.getConfigManager().getBorderParticleSpacing();

        for (int y = minY; y <= maxY; y += step) {
            spawnBorderParticle(player, world, minX, y, minZ);
            spawnBorderParticle(player, world, minX, y, maxZ);
            spawnBorderParticle(player, world, maxX, y, minZ);
            spawnBorderParticle(player, world, maxX, y, maxZ);
        }
        for (int x = minX; x <= maxX; x += step) {
            spawnBorderParticle(player, world, x, minY, minZ);
            spawnBorderParticle(player, world, x, minY, maxZ);
            spawnBorderParticle(player, world, x, maxY, minZ);
            spawnBorderParticle(player, world, x, maxY, maxZ);
        }
        for (int z = minZ; z <= maxZ; z += step) {
            spawnBorderParticle(player, world, minX, minY, z);
            spawnBorderParticle(player, world, maxX, minY, z);
            spawnBorderParticle(player, world, minX, maxY, z);
            spawnBorderParticle(player, world, maxX, maxY, z);
        }
    }

    private void spawnBorderParticle(Player player, org.bukkit.World world, int x, int y, int z) {
        player.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER,
                x + 0.5, y + 0.5, z + 0.5, 1, 0, 0, 0, 0);
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
        Block block = event.getBlock();
        Claim claim = plugin.getClaimManager().getClaimAt(block.getLocation());

        boolean isCore = claim != null && claim.isCoreBlock(block.getX(), block.getY(),
                block.getZ(), block.getWorld().getName());
        boolean wieldingClaimBreaker = hasClaimBreaker(player.getInventory().getItemInMainHand());

        // The Claim Breaker's job is breaking claim cores. It's ALSO the only thing that
        // can break TNT-immune blocks (obsidian, ancient debris, etc.) - people wall
        // those in as raid-proofing, so the tool needs to be able to clear them. Bedrock
        // is never breakable, no exceptions.
        if (wieldingClaimBreaker && !isCore) {
            if (isTntImmune(block.getType())) {
                // Even with the Claim Breaker, a Peaceful claim's protection is absolute.
                boolean memberOrBypass = claim != null
                        && (claim.isMember(player.getUniqueId()) || bypasses(player));
                if (claim != null && claim.getType() == ClaimType.PEACEFUL && !memberOrBypass) {
                    event.setCancelled(true);
                    Msg.send(player, "&cThis is a peaceful claim - it cannot be broken into.");
                    return;
                }
                if (claim != null && block.getType() == Material.OBSIDIAN) claim.decrementObsidian();
                if (claim != null && block.getType() == Material.BEDROCK) claim.decrementBedrock();
                return; // allowed - vanilla break proceeds
            }
            event.setCancelled(true);
            Msg.send(player, "&cThis is not a claim block; you cannot break non-claimblocks with the Claim Breaker. It's in the name...");
            return;
        }

        if (claim == null) return;

        boolean isMember = claim.isMember(player.getUniqueId());
        boolean isBypass = bypasses(player);

        if (isCore) {
            boolean isRaidBreak = !isMember && !isBypass && claim.getType() == ClaimType.PVP
                    && wieldingClaimBreaker && isPvpPlayer(player);

            if (isMember || isBypass || isRaidBreak) {
                // Core destroyed -> the claim ceases to exist. Cancel the vanilla break
                // and delete the claim ourselves so the block just stops existing
                // (no item drop, no leftover "plain" beacon sitting there).
                event.setCancelled(true);
                plugin.getClaimManager().removeClaim(claim);

                if (isRaidBreak) {
                    Msg.send(player, "&a&lRaid Successful! &7You destroyed a player's claim core.");
                    notifyOwnerOfRaid(claim);
                } else {
                    Msg.send(player, "&eClaim core broken. Your claim has been deleted.");
                }
                return;
            }

            event.setCancelled(true);
            Msg.send(player, "&cYou cannot break another player's claim core.");
            return;
        }

        if (isMember || isBypass) {
            if (block.getType() == Material.OBSIDIAN) claim.decrementObsidian();
            if (block.getType() == Material.BEDROCK) claim.decrementBedrock();
            return;
        }

        if (claim.getType() == ClaimType.PEACEFUL) {
            event.setCancelled(true);
            Msg.send(player, "&cThis is a peaceful claim - it cannot be broken into.");
            return;
        }

        // PVP claim, non-core block: hand-mining isn't a thing anymore (the Claim
        // Breaker only touches cores now) - raiding regular blocks is done via TNT.
        event.setCancelled(true);
        Msg.send(player, "&cYou don't have permission to break blocks in this claim. Try blowing it up.");
    }

    /** Lets a raided claim's owner know their core was destroyed, without naming the raider. */
    private void notifyOwnerOfRaid(Claim claim) {
        Player owner = plugin.getServer().getPlayer(claim.getOwner());
        if (owner != null) {
            Msg.send(owner, "&c&lYour claim was raided! &7Your claim core has been destroyed and the claim deleted.");
        }
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
        boolean isExtraCoreItem = meta != null && meta.getPersistentDataContainer()
                .has(plugin.getExtraCoreBlockKey(), PersistentDataType.STRING);

        if (isCoreItem) {
            handleCorePlacement(event, player);
            return;
        }
        if (isExtraCoreItem) {
            handleExtraCorePlacement(event, player);
            return;
        }

        if (bypasses(player)) return;

        Location placedLoc = event.getBlockPlaced().getLocation();
        Material placedType = event.getBlockPlaced().getType();
        Claim claim = plugin.getClaimManager().getClaimAt(placedLoc);

        if (claim == null) {
            // Not inside any claim - but obsidian still can't go in the buffer band
            // immediately outside a claim's border (see ClaimManager#getClaimInBuffer),
            // whether it's your own claim or someone else's. This is what stops the
            // "obsidian-box the outside of my claim so it can never be raided" trick.
            if (placedType == Material.OBSIDIAN) {
                int buffer = plugin.getConfigManager().getObsidianBufferRadius();
                if (buffer > 0 && plugin.getClaimManager().getClaimInBuffer(placedLoc, buffer) != null) {
                    event.setCancelled(true);
                    Msg.send(player, "&cYou can't place obsidian this close to a claim border.");
                    return;
                }
            }
            return;
        }

        if (claim.isMember(player.getUniqueId())) {
            if (placedType == Material.OBSIDIAN) {
                int max = plugin.getConfigManager().getMaxObsidianPerClaim();
                if (max > 0 && claim.getObsidianCount() >= max) {
                    event.setCancelled(true);
                    Msg.send(player, "&cThis claim already has the max of " + max + " obsidian blocks.");
                    return;
                }
                claim.incrementObsidian();
            }
            if (placedType == Material.BEDROCK) {
                int max = plugin.getConfigManager().getMaxBedrockPerClaim();
                if (claim.getBedrockCount() >= max) {
                    event.setCancelled(true);
                    Msg.send(player, max <= 0
                            ? "&cBedrock can't be placed inside a claim."
                            : "&cThis claim already has the max of " + max + " bedrock blocks.");
                    return;
                }
                claim.incrementBedrock();
            }
            return;
        }

        if (placedType == Material.TNT) {
            // PVP claims can be raided by placing/lighting TNT inside them - allowed for
            // any PVP-flagged player until the claim's core is broken. Peaceful claims
            // never allow this, from anyone.
            if (claim.getType() == ClaimType.PVP && isPvpPlayer(player)) {
                return;
            }
            event.setCancelled(true);
            Msg.send(player, claim.getType() == ClaimType.PEACEFUL
                    ? "&cThis is a peaceful claim - TNT cannot be placed here."
                    : "&cOnly PVP players can place TNT inside this claim.");
            return;
        }

        event.setCancelled(true);
        Msg.send(player, "&cYou can't place blocks in this claim.");
    }

    // ---------------------------------------------------------------
    // Lighting existing TNT blocks by hand inside a claim. Peaceful claims never allow
    // this. PVP claims allow it for any PVP-flagged player (that's how you raid them),
    // right up until the core is broken and the claim ceases to exist.
    // ---------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH)
    public void onTntIgnite(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.TNT) return;

        ItemStack item = event.getItem();
        if (item == null || (item.getType() != Material.FLINT_AND_STEEL && item.getType() != Material.FIRE_CHARGE)) return;

        Player player = event.getPlayer();
        if (bypasses(player)) return;

        Claim claim = plugin.getClaimManager().getClaimAt(block.getLocation());
        if (claim == null) return;
        if (claim.isMember(player.getUniqueId())) return;

        if (claim.getType() == ClaimType.PVP && isPvpPlayer(player)) {
            return;
        }

        event.setCancelled(true);
        Msg.send(player, claim.getType() == ClaimType.PEACEFUL
                ? "&cThis is a peaceful claim - TNT cannot be lit here."
                : "&cOnly PVP players can light TNT inside this claim.");
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

    /** Places an "extra" (purchased) claim core - same overlap rules, but never inherits
     *  another claim's size and is tracked separately from your primary claim. */
    private void handleExtraCorePlacement(BlockPlaceEvent event, Player player) {
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
        claim.setPrimary(false);
        plugin.getClaimManager().addClaim(claim);
        Msg.send(player, "&aExtra claim created! Size: " + size + "x" + size + "x" + size
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

    // End crystals are a valid raiding method too (crystal PvP style base-breaking) -
    // whoever detonates one (melee or projectile) is tagged as the "primer", the exact
    // same way TNT is, so onExplode below treats it identically. This also means it
    // naturally works when the crystal is detonated from outside the claim - onExplode
    // checks each affected block's claim membership individually, not the crystal's
    // location.
    @EventHandler
    public void onCrystalDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.EnderCrystal crystal)) return;
        Player attacker = resolvePlayerAttacker(event.getDamager());
        if (attacker == null) return;
        crystal.setMetadata("incogclaims_primer",
                new org.bukkit.metadata.FixedMetadataValue(plugin, attacker.getUniqueId().toString()));
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
                continue;
            }
            // else: allowed to blow up - raid succeeds
            if (b.getType() == Material.OBSIDIAN) claim.decrementObsidian();
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

    // Blocks that survive a standard TNT explosion in vanilla. Bedrock is deliberately
    // excluded - the Claim Breaker must never be able to touch it.
    private static final Set<Material> TNT_IMMUNE = EnumSet.of(
            Material.OBSIDIAN,
            Material.CRYING_OBSIDIAN,
            Material.ANCIENT_DEBRIS,
            Material.NETHERITE_BLOCK,
            Material.RESPAWN_ANCHOR,
            Material.REINFORCED_DEEPSLATE,
            Material.ENCHANTING_TABLE
    );

    private boolean isTntImmune(Material type) {
        if (type == Material.BEDROCK) return false;
        return TNT_IMMUNE.contains(type);
    }

    // ---------------------------------------------------------------
    // Claim enter notification - "You have entered a Peaceful/Aggressive claim (size)".
    // Never reveals the owner's name or the claim's location.
    // ---------------------------------------------------------------
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        try {
            // PlayerMoveEvent#getTo() can be null (some teleport edge cases) - this was
            // almost certainly the actual cause of the "Could not pass event
            // PlayerMoveEvent" log spam and TPS drop, since an NPE here fires on every
            // single move for every player.
            Location to = event.getTo();
            Location from = event.getFrom();
            if (to == null || to.getWorld() == null || from == null) return;

            if (from.getBlockX() == to.getBlockX()
                    && from.getBlockY() == to.getBlockY()
                    && from.getBlockZ() == to.getBlockZ()) {
                return; // only care about actual block-position changes
            }

            Player player = event.getPlayer();
            Claim claim = plugin.getClaimManager().getClaimAt(to);
            UUID newId = claim == null ? null : claim.getId();
            UUID oldId = currentClaim.put(player.getUniqueId(), newId);

            if (newId != null && !newId.equals(oldId)) {
                String typeName = claim.getType() == ClaimType.PVP ? "&c&lAggressive" : "&a&lPeaceful";
                Msg.actionBar(player, "&7You have entered a " + typeName + "&7 claim &f(" + claim.getSize()
                        + "x" + claim.getSize() + "x" + claim.getSize() + ")");
            }
        } catch (Exception e) {
            // Never let this handler spam the console/TPS - log once, then stay quiet.
            if (!loggedMoveError) {
                loggedMoveError = true;
                plugin.getLogger().warning("Incog-Claims: suppressing repeated errors in claim-enter tracking: " + e);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        currentClaim.remove(event.getPlayer().getUniqueId());
    }
}
