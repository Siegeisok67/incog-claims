package com.incogdev.incogclaims.data;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class Claim {

    private final UUID id;
    private UUID owner;
    private ClaimType type;
    private String world;
    private int coreX, coreY, coreZ;
    private int size; // cube edge length, centered on core
    private final Set<UUID> trusted = new HashSet<>();
    private boolean primary = true; // false for purchased "extra" claims
    private int obsidianCount = 0;   // live count of obsidian blocks placed inside this claim
    private int bedrockCount = 0;    // live count of bedrock blocks placed inside this claim

    public Claim(UUID id, UUID owner, ClaimType type, Location core, int size) {
        this.id = id;
        this.owner = owner;
        this.type = type;
        this.world = core.getWorld().getName();
        this.coreX = core.getBlockX();
        this.coreY = core.getBlockY();
        this.coreZ = core.getBlockZ();
        this.size = size;
    }

    public UUID getId() { return id; }
    public UUID getOwner() { return owner; }
    public ClaimType getType() { return type; }
    public void setType(ClaimType type) { this.type = type; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public Set<UUID> getTrusted() { return trusted; }

    public boolean isPrimary() { return primary; }
    public void setPrimary(boolean primary) { this.primary = primary; }

    public int getObsidianCount() { return obsidianCount; }
    public void incrementObsidian() { obsidianCount++; }
    public void decrementObsidian() { if (obsidianCount > 0) obsidianCount--; }

    public int getBedrockCount() { return bedrockCount; }
    public void incrementBedrock() { bedrockCount++; }
    public void decrementBedrock() { if (bedrockCount > 0) bedrockCount--; }

    public String getWorldName() { return world; }
    public int getCoreX() { return coreX; }
    public int getCoreY() { return coreY; }
    public int getCoreZ() { return coreZ; }

    public Location getCoreLocation() {
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, coreX, coreY, coreZ);
    }

    public boolean isMember(UUID uuid) {
        return owner.equals(uuid) || trusted.contains(uuid);
    }

    public boolean isCoreBlock(int x, int y, int z, String worldName) {
        return worldName.equals(world) && x == coreX && y == coreY && z == coreZ;
    }

    /** Half-extent of the cube, e.g. size 48 -> reaches 24 blocks each direction from core. */
    public int getHalf() {
        return size / 2;
    }

    public boolean contains(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(world)) return false;
        int half = getHalf();
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        return x >= coreX - half && x <= coreX + half
                && z >= coreZ - half && z <= coreZ + half
                && y >= coreY - half && y <= coreY + half;
    }

    public boolean overlapsCube(String worldName, int cx, int cy, int cz, int otherSize) {
        if (!worldName.equals(world)) return false;
        int half = getHalf();
        int otherHalf = otherSize / 2;
        boolean xOverlap = Math.abs(cx - coreX) <= (half + otherHalf);
        boolean yOverlap = Math.abs(cy - coreY) <= (half + otherHalf);
        boolean zOverlap = Math.abs(cz - coreZ) <= (half + otherHalf);
        return xOverlap && yOverlap && zOverlap;
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id.toString());
        map.put("owner", owner.toString());
        map.put("type", type.name());
        map.put("world", world);
        map.put("coreX", coreX);
        map.put("coreY", coreY);
        map.put("coreZ", coreZ);
        map.put("size", size);
        map.put("trusted", trusted.stream().map(UUID::toString).collect(Collectors.toList()));
        map.put("primary", primary);
        map.put("obsidianCount", obsidianCount);
        map.put("bedrockCount", bedrockCount);
        return map;
    }

    @SuppressWarnings("unchecked")
    public static Claim deserialize(Map<String, Object> map) {
        UUID id = UUID.fromString((String) map.get("id"));
        UUID owner = UUID.fromString((String) map.get("owner"));
        ClaimType type = ClaimType.fromString((String) map.get("type"));
        String world = (String) map.get("world");
        int cx = ((Number) map.get("coreX")).intValue();
        int cy = ((Number) map.get("coreY")).intValue();
        int cz = ((Number) map.get("coreZ")).intValue();
        int size = ((Number) map.get("size")).intValue();

        World w = Bukkit.getWorld(world);
        Location loc = new Location(w, cx, cy, cz);
        Claim claim = new Claim(id, owner, type, loc, size);
        Object trustedObj = map.get("trusted");
        if (trustedObj instanceof Iterable) {
            for (Object o : (Iterable<Object>) trustedObj) {
                try { claim.getTrusted().add(UUID.fromString(o.toString())); } catch (Exception ignored) {}
            }
        }
        // Missing "primary" (claims saved before this field existed) defaults to true,
        // so nobody's existing claim silently turns into an untracked "extra" claim.
        Object primaryObj = map.get("primary");
        claim.setPrimary(primaryObj == null || Boolean.parseBoolean(primaryObj.toString()));
        Object obsidianObj = map.get("obsidianCount");
        if (obsidianObj instanceof Number) {
            for (int i = 0; i < ((Number) obsidianObj).intValue(); i++) claim.incrementObsidian();
        }
        Object bedrockObj = map.get("bedrockCount");
        if (bedrockObj instanceof Number) {
            for (int i = 0; i < ((Number) bedrockObj).intValue(); i++) claim.incrementBedrock();
        }
        return claim;
    }
}
