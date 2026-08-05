package com.incogdev.incogclaims.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerDataManager {

    private final Map<UUID, PlayerData> data = new HashMap<>();

    public PlayerData get(UUID uuid) {
        return data.computeIfAbsent(uuid, PlayerData::new);
    }

    public boolean isLoaded(UUID uuid) {
        return data.containsKey(uuid);
    }

    public void put(UUID uuid, PlayerData pd) {
        data.put(uuid, pd);
    }

    public Map<UUID, PlayerData> getAll() {
        return data;
    }
}
