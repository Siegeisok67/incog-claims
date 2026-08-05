package com.incogdev.incogclaims.data;

import org.bukkit.Location;

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

    public void removeClaim(Claim claim) {
        claimsById.remove(claim.getId());
        claimsByOwner.remove(claim.getOwner());
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
