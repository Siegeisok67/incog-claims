package com.incogdev.incogclaims.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.*;

public class ClaimManager {

    private final Map<UUID, Claim> claimsById = new HashMap<>();
    private final Map<UUID, Claim> claimsByOwner = new HashMap<>();

    public Claim getClaimByOwner(UUID owner) {
        return claimsByOwner.get(owner);
    }

    public Claim getClaimById(UUID id) {
        return claimsById.get(id);
    }

    public Collection<Claim> getAllClaims() {
        return claimsById.values();
    }

    public boolean hasClaim(UUID owner) {
        return claimsByOwner.containsKey(owner);
    }

    public void addClaim(Claim claim) {
        claimsById.put(claim.getId(), claim);
        claimsByOwner.put(claim.getOwner(), claim);
    }

    /**
     * Deletes a claim entirely: removes it from the data maps AND makes sure its core
     * block actually stops existing in the world. Previously the core block (e.g. the
     * beacon) was left behind as a plain, unprotected block after deletion - now it's
     * cleared to air so nothing lingers.
     */
    public void removeClaim(Claim claim) {
        claimsById.remove(claim.getId());
        claimsByOwner.remove(claim.getOwner());
        clearCoreBlock(claim);
    }

    private void clearCoreBlock(Claim claim) {
        World world = Bukkit.getWorld(claim.getWorldName());
        if (world == null) return;
        world.getBlockAt(claim.getCoreX(), claim.getCoreY(), claim.getCoreZ()).setType(Material.AIR, false);
    }

    /** Finds the claim (if any) that contains the given location. */
    public Claim getClaimAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        for (Claim c : claimsById.values()) {
            if (c.contains(loc)) return c;
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
}
