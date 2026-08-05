package com.incogdev.incogclaims.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerData {

    private final UUID uuid;
    private ClaimType type;
    private boolean hasChosenType;
    private long lastSwitchTimestamp; // millis, 0 = never switched
    private long firstJoin;
    private int claimBlocks;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        this.type = null;
        this.hasChosenType = false;
        this.lastSwitchTimestamp = 0L;
        this.firstJoin = System.currentTimeMillis();
        this.claimBlocks = 0;
    }

    public UUID getUuid() { return uuid; }

    public ClaimType getType() { return type; }
    public void setType(ClaimType type) { this.type = type; }

    public boolean hasChosenType() { return hasChosenType; }
    public void setHasChosenType(boolean b) { this.hasChosenType = b; }

    public long getLastSwitchTimestamp() { return lastSwitchTimestamp; }
    public void setLastSwitchTimestamp(long t) { this.lastSwitchTimestamp = t; }

    public long getFirstJoin() { return firstJoin; }
    public void setFirstJoin(long firstJoin) { this.firstJoin = firstJoin; }

    public int getClaimBlocks() { return claimBlocks; }
    public void setClaimBlocks(int claimBlocks) { this.claimBlocks = claimBlocks; }
    public void addClaimBlocks(int amount) { this.claimBlocks += amount; }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type == null ? "NONE" : type.name());
        map.put("hasChosenType", hasChosenType);
        map.put("lastSwitchTimestamp", lastSwitchTimestamp);
        map.put("firstJoin", firstJoin);
        map.put("claimBlocks", claimBlocks);
        return map;
    }

    public static PlayerData deserialize(UUID uuid, Map<String, Object> map) {
        PlayerData pd = new PlayerData(uuid);
        Object typeObj = map.get("type");
        if (typeObj != null && !typeObj.toString().equalsIgnoreCase("NONE")) {
            pd.setType(ClaimType.fromString(typeObj.toString()));
        }
        pd.setHasChosenType(Boolean.TRUE.equals(map.get("hasChosenType")));
        pd.setLastSwitchTimestamp(toLong(map.get("lastSwitchTimestamp")));
        pd.setFirstJoin(toLong(map.get("firstJoin")));
        Object cb = map.get("claimBlocks");
        pd.setClaimBlocks(cb == null ? 0 : ((Number) cb).intValue());
        return pd;
    }

    private static long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(o.toString()); } catch (Exception e) { return 0L; }
    }
}
