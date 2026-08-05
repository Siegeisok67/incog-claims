package com.incogdev.incogclaims.data;

import com.incogdev.incogclaims.IncogClaims;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class StorageManager {

    private final IncogClaims plugin;
    private final File claimsFile;
    private final File playersFile;

    public StorageManager(IncogClaims plugin) {
        this.plugin = plugin;
        this.claimsFile = new File(plugin.getDataFolder(), "claims.yml");
        this.playersFile = new File(plugin.getDataFolder(), "players.yml");
    }

    public void loadAll() {
        loadClaims();
        loadPlayers();
    }

    public void saveAll() {
        saveClaims();
        savePlayers();
    }

    private void loadClaims() {
        if (!claimsFile.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(claimsFile);
        ConfigurationSection section = yml.getConfigurationSection("claims");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            ConfigurationSection cs = section.getConfigurationSection(key);
            if (cs == null) continue;
            try {
                Map<String, Object> map = cs.getValues(false);
                Claim claim = Claim.deserialize(map);
                plugin.getClaimManager().addClaim(claim);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load claim " + key, e);
            }
        }
    }

    private void saveClaims() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Claim claim : plugin.getClaimManager().getAllClaims()) {
            String path = "claims." + claim.getId();
            for (Map.Entry<String, Object> entry : claim.serialize().entrySet()) {
                yml.set(path + "." + entry.getKey(), entry.getValue());
            }
        }
        try {
            yml.save(claimsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save claims.yml", e);
        }
    }

    private void loadPlayers() {
        if (!playersFile.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(playersFile);
        ConfigurationSection section = yml.getConfigurationSection("players");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            ConfigurationSection cs = section.getConfigurationSection(key);
            if (cs == null) continue;
            try {
                UUID uuid = UUID.fromString(key);
                Map<String, Object> map = cs.getValues(false);
                PlayerData pd = PlayerData.deserialize(uuid, map);
                plugin.getPlayerDataManager().put(uuid, pd);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load player data " + key, e);
            }
        }
    }

    private void savePlayers() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerData> entry : plugin.getPlayerDataManager().getAll().entrySet()) {
            String path = "players." + entry.getKey();
            for (Map.Entry<String, Object> e : entry.getValue().serialize().entrySet()) {
                yml.set(path + "." + e.getKey(), e.getValue());
            }
        }
        try {
            yml.save(playersFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save players.yml", e);
        }
    }
}
