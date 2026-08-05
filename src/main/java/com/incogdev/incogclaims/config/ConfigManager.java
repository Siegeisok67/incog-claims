package com.incogdev.incogclaims.config;

import com.incogdev.incogclaims.IncogClaims;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public class ConfigManager {

    private final IncogClaims plugin;

    private Material claimCoreMaterial;
    private final Map<Integer, Integer> claimSizes = new TreeMap<>(); // size -> cost
    private int earnIntervalMinutes;
    private int earnAmount;
    private int switchCooldownDays;
    private boolean tntRaidEnabled;
    private boolean requirePvpAttackerToRaid;
    private boolean opBypass;
    private double claimbreakerLootChance;

    public ConfigManager(IncogClaims plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        var cfg = plugin.getConfig();

        String matName = cfg.getString("claim-core-material", "BEACON");
        Material mat = Material.matchMaterial(matName);
        this.claimCoreMaterial = mat != null ? mat : Material.BEACON;

        claimSizes.clear();
        ConfigurationSection sizesSection = cfg.getConfigurationSection("claim-sizes");
        if (sizesSection != null) {
            for (String key : sizesSection.getKeys(false)) {
                try {
                    int size = Integer.parseInt(key);
                    int cost = sizesSection.getInt(key);
                    claimSizes.put(size, cost);
                } catch (NumberFormatException ignored) {}
            }
        }
        if (claimSizes.isEmpty()) {
            claimSizes.put(48, 0);
            claimSizes.put(96, 500);
        }

        earnIntervalMinutes = cfg.getInt("earn-interval-minutes", 10);
        earnAmount = cfg.getInt("earn-amount", 10);
        switchCooldownDays = cfg.getInt("switch-cooldown-days", 3);
        tntRaidEnabled = cfg.getBoolean("tnt-raid-enabled", true);
        requirePvpAttackerToRaid = cfg.getBoolean("require-pvp-attacker-to-raid", true);
        opBypass = cfg.getBoolean("op-bypass", true);
        claimbreakerLootChance = cfg.getDouble("claimbreaker-loot-chance", 0.0015);
    }

    public Material getClaimCoreMaterial() { return claimCoreMaterial; }
    public Map<Integer, Integer> getClaimSizes() { return new LinkedHashMap<>(claimSizes); }
    public int getSmallestSize() { return claimSizes.keySet().stream().min(Integer::compareTo).orElse(48); }
    public int getEarnIntervalMinutes() { return earnIntervalMinutes; }
    public int getEarnAmount() { return earnAmount; }
    public int getSwitchCooldownDays() { return switchCooldownDays; }
    public boolean isTntRaidEnabled() { return tntRaidEnabled; }
    public boolean isRequirePvpAttackerToRaid() { return requirePvpAttackerToRaid; }
    public boolean isOpBypass() { return opBypass; }
    public double getClaimbreakerLootChance() { return claimbreakerLootChance; }

    public String getMessage(String path) {
        return plugin.getConfig().getString("messages." + path, "");
    }
}
