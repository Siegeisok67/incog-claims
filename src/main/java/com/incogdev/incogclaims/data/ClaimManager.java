package com.incogdev.incogclaims.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.*;

public class ClaimManager {

    private final Map<UUID, Claim> claimsById = new HashMap<>();
    private final Map<UUID, Claim> claimsByOwner = new HashMap<>();          // one "primary" claim per owner
    private final Map<UUID, List<Claim>> extraClaimsByOwner = new HashMap<>(); // purchased additional claims

    // Spatial index: "world:chunkX:chunkZ" -> claim IDs whose cuboid overlaps that chunk.
    // getClaimAt() used to do a linear scan over every claim on the server on every single
    // PlayerMoveEvent, which is what was tanking TPS with more than a handful of claims.
    // Now it only checks the handful of claims that actually touch the chunk you're in.
    private final Map<String, Set<UUID>> chunkIndex = new HashMap<>();

    public Claim getClaimByOwner(UUID owner) {
        return claimsByOwner.get(owner);
    }

    public Claim getClaimById(UUID id) {
        return claimsById.get(id);
    }

    public Collection<Claim> getAllClaims() {
        return claimsById.values();
    }

    /** True if this player owns their one "free" primary claim (from /iclaims core). */
    public boolean hasClaim(UUID owner) {
        return claimsByOwner.containsKey(owner);
    }

    /** All claims (primary + any purchased extras) owned by this player. */
    public List<Claim> getClaimsOwnedBy(UUID owner) {
        List<Claim> list = new ArrayList<>();
        Claim primary = claimsByOwner.get(owner);
        if (primary != null) list.add(primary);
        list.addAll(extraClaimsByOwner.getOrDefault(owner, Collections.emptyList()));
        return list;
    }

    public int countExtraClaims(UUID owner) {
        return extraClaimsByOwner.getOrDefault(owner, Collections.emptyList()).size();
    }

    public void addClaim(Claim claim) {
        claimsById.put(claim.getId(), claim);
        if (claim.isPrimary()) {
            claimsByOwner.put(claim.getOwner(), claim);
        } else {
            extraClaimsByOwner.computeIfAbsent(claim.getOwner(), k -> new ArrayList<>()).add(claim);
        }
        indexClaim(claim);
    }

    /**
     * Deletes a claim entirely: removes it from the data maps AND makes sure its core
     * block actually stops existing in the world. Previously the core block (e.g. the
     * beacon) was left behind as a plain, unprotected block after deletion - now it's
     * cleared to air (no drop) so nothing lingers.
     */
    public void removeClaim(Claim claim) {
        deindexClaim(claim);
        claimsById.remove(claim.getId());
        if (claim.isPrimary()) {
            claimsByOwner.remove(claim.getOwner());
        } else {
            List<Claim> list = extraClaimsByOwner.get(claim.getOwner());
            if (list != null) list.remove(claim);
        }
        clearCoreBlock(claim);
    }

    /** Call instead of claim.setSize() directly - keeps the spatial index in sync. */
    public void resizeClaim(Claim claim, int newSize) {
        deindexClaim(claim);
        claim.setSize(newSize);
        indexClaim(claim);
    }

    private void clearCoreBlock(Claim claim) {
        World world = Bukkit.getWorld(claim.getWorldName());
        if (world == null) return;
        world.getBlockAt(claim.getCoreX(), claim.getCoreY(), claim.getCoreZ()).setType(Material.AIR, false);
    }

    /** Finds the claim (if any) that contains the given location. */
    public Claim getClaimAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        Set<UUID> candidates = chunkIndex.get(chunkKey(loc.getWorld().getName(),
                loc.getBlockX() >> 4, loc.getBlockZ() >> 4));
        if (candidates == null || candidates.isEmpty()) return null;
        for (UUID id : candidates) {
            Claim c = claimsById.get(id);
            if (c != null && c.contains(loc)) return c;
        }
        return null;
    }

    /**
     * Finds a claim (if any) whose horizontal "keep clear" buffer (see
     * Claim#isInHorizontalBuffer) contains this location. Used to block obsidian
     * placement just outside a claim's border. Searches every chunk within range of the
     * padding, not just the location's own chunk, so it still catches claims whose real
     * border - not just their indexed chunk - is within `padding` blocks of loc.
     */
    public Claim getClaimInBuffer(Location loc, int padding) {
        if (loc == null || loc.getWorld() == null || padding <= 0) return null;

        int chunkRadius = (padding >> 4) + 1;
        int baseCx = loc.getBlockX() >> 4, baseCz = loc.getBlockZ() >> 4;
        String worldName = loc.getWorld().getName();

        Set<UUID> candidates = new HashSet<>();
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                Set<UUID> inChunk = chunkIndex.get(chunkKey(worldName, baseCx + dx, baseCz + dz));
                if (inChunk != null) candidates.addAll(inChunk);
            }
        }

        for (UUID id : candidates) {
            Claim c = claimsById.get(id);
            if (c != null && c.isInHorizontalBuffer(loc, padding)) return c;
        }
        return null;
    }

    /** Checks whether a proposed new/resized cube would overlap any OTHER existing claim. */
    public boolean wouldOverlap(Claim ignore, String world, int cx, int cy, int cz, int size) {
        for (Claim c : claimsById.values()) {
            if (c.equals(ignore)) continue;
            if (c.overlapsCube(world, cx, cy, cz, size)) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------
    // Spatial index maintenance
    // ---------------------------------------------------------------
    private String chunkKey(String world, int chunkX, int chunkZ) {
        return world + ":" + chunkX + ":" + chunkZ;
    }

    private void indexClaim(Claim claim) {
        forEachChunk(claim, key -> chunkIndex.computeIfAbsent(key, k -> new HashSet<>()).add(claim.getId()));
    }

    private void deindexClaim(Claim claim) {
        forEachChunk(claim, key -> {
            Set<UUID> set = chunkIndex.get(key);
            if (set == null) return;
            set.remove(claim.getId());
            if (set.isEmpty()) chunkIndex.remove(key);
        });
    }

    private void forEachChunk(Claim claim, java.util.function.Consumer<String> action) {
        int half = claim.getHalf();
        int minChunkX = (claim.getCoreX() - half) >> 4;
        int maxChunkX = (claim.getCoreX() + half) >> 4;
        int minChunkZ = (claim.getCoreZ() - half) >> 4;
        int maxChunkZ = (claim.getCoreZ() + half) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                action.accept(chunkKey(claim.getWorldName(), cx, cz));
            }
        }
    }
}
