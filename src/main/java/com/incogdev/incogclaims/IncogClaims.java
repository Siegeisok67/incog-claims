package com.incogdev.incogclaims;

import com.incogdev.incogclaims.commands.IClaimsCommand;
import com.incogdev.incogclaims.config.ConfigManager;
import com.incogdev.incogclaims.data.ClaimManager;
import com.incogdev.incogclaims.data.PlayerDataManager;
import com.incogdev.incogclaims.data.StorageManager;
import com.incogdev.incogclaims.listeners.EnchantListener;
import com.incogdev.incogclaims.listeners.GUIListener;
import com.incogdev.incogclaims.listeners.JoinListener;
import com.incogdev.incogclaims.listeners.ProtectionListener;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public class IncogClaims extends JavaPlugin {

    private static IncogClaims instance;

    private ConfigManager configManager;
    private ClaimManager claimManager;
    private PlayerDataManager playerDataManager;
    private StorageManager storageManager;

    private NamespacedKey coreBlockKey;
    private NamespacedKey claimBreakerKey;

    @Override
    public void onEnable() {
        instance = this;

        this.coreBlockKey = new NamespacedKey(this, "claim_core");
        this.claimBreakerKey = new NamespacedKey(this, "claim_breaker");

        this.configManager = new ConfigManager(this);
        this.configManager.load();

        this.claimManager = new ClaimManager();
        this.playerDataManager = new PlayerDataManager();
        this.storageManager = new StorageManager(this);
        this.storageManager.loadAll();

        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(new EnchantListener(this), this);

        IClaimsCommand command = new IClaimsCommand(this);
        getCommand("iclaims").setExecutor(command);
        getCommand("iclaims").setTabCompleter(command);

        startEarnTask();
        startAutosaveTask();

        getLogger().info("Incog-Claims v" + getDescription().getVersion() + " enabled. By Siegeisok67 and the Incog Dev Team.");
    }

    @Override
    public void onDisable() {
        if (storageManager != null) storageManager.saveAll();
        getLogger().info("Incog-Claims disabled. Data saved. Credit: Siegeisok67 & Incog Dev Team.");
    }

    private void startEarnTask() {
        long interval = 20L * 60L * Math.max(1, configManager.getEarnIntervalMinutes());
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (var player : getServer().getOnlinePlayers()) {
                if (claimManager.hasClaim(player.getUniqueId())) {
                    playerDataManager.get(player.getUniqueId()).addClaimBlocks(configManager.getEarnAmount());
                }
            }
        }, interval, interval);
    }

    private void startAutosaveTask() {
        long interval = 20L * 60L * 5L; // every 5 minutes
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> storageManager.saveAll(), interval, interval);
    }

    public static IncogClaims getInstance() { return instance; }

    public ConfigManager getConfigManager() { return configManager; }
    public ClaimManager getClaimManager() { return claimManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public StorageManager getStorageManager() { return storageManager; }
    public NamespacedKey getCoreBlockKey() { return coreBlockKey; }
    public NamespacedKey getClaimBreakerKey() { return claimBreakerKey; }
}
