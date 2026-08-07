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
    private NamespacedKey extraCoreBlockKey;
    private NamespacedKey claimBreakerKey;
    private ProtectionListener protectionListener;

    @Override
    public void onEnable() {
        instance = this;

        this.coreBlockKey = new NamespacedKey(this, "claim_core");
        this.extraCoreBlockKey = new NamespacedKey(this, "extra_claim_core");
        this.claimBreakerKey = new NamespacedKey(this, "claim_breaker");

        this.configManager = new ConfigManager(this);
        this.configManager.load();

        this.claimManager = new ClaimManager();
        this.playerDataManager = new PlayerDataManager();
        this.storageManager = new StorageManager(this);
        this.storageManager.loadAll();

        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        this.protectionListener = new ProtectionListener(this);
        getServer().getPluginManager().registerEvents(protectionListener, this);
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(new EnchantListener(this), this);
        EnchantListener.registerRecipe(this);

        IClaimsCommand command = new IClaimsCommand(this);
        getCommand("iclaims").setExecutor(command);
        getCommand("iclaims").setTabCompleter(command);

        startEarnTask();
        startAutosaveTask();
        startBorderTask();

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
            int cap = configManager.getMaxClaimBlocks();
            for (var player : getServer().getOnlinePlayers()) {
                if (claimManager.hasClaim(player.getUniqueId())) {
                    var data = playerDataManager.get(player.getUniqueId());
                    int newAmount = Math.min(cap, data.getClaimBlocks() + configManager.getEarnAmount());
                    data.setClaimBlocks(newAmount);
                }
            }
        }, interval, interval);
    }

    private void startAutosaveTask() {
        long interval = 20L * 60L * 5L; // every 5 minutes
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> storageManager.saveAll(), interval, interval);
    }

    private void startBorderTask() {
        // Redraws claim-border particles for anyone with /iclaims showclaim toggled on.
        // Runs on the main thread (particle spawning is not thread-safe) every second -
        // frequent enough to look like a steady outline without spamming packets.
        getServer().getScheduler().runTaskTimer(this, protectionListener::tickBorders, 20L, 20L);
    }

    public static IncogClaims getInstance() { return instance; }

    public ConfigManager getConfigManager() { return configManager; }
    public ClaimManager getClaimManager() { return claimManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public StorageManager getStorageManager() { return storageManager; }
    public NamespacedKey getCoreBlockKey() { return coreBlockKey; }
    public NamespacedKey getExtraCoreBlockKey() { return extraCoreBlockKey; }
    public NamespacedKey getClaimBreakerKey() { return claimBreakerKey; }
    public ProtectionListener getProtectionListener() { return protectionListener; }
}
