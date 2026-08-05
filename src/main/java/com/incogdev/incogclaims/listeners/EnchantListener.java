package com.incogdev.incogclaims.listeners;

import com.incogdev.incogclaims.IncogClaims;
import com.incogdev.incogclaims.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Random;

public class EnchantListener implements Listener {

    private final IncogClaims plugin;
    private final Random random = new Random();

    public EnchantListener(IncogClaims plugin) {
        this.plugin = plugin;
    }

    /** Builds the rare Claim Breaker pickaxe - lets PVP players hand-mine blocks in PVP claims. */
    public static ItemStack buildClaimBreakerPickaxe(IncogClaims plugin) {
        ItemStack item = new ItemBuilder(Material.NETHERITE_PICKAXE)
                .name("&5&l\u2726 Claim Breaker \u2726")
                .lore(
                        "&7An impossibly rare tool said to be",
                        "&7forged by raiders themselves.",
                        "",
                        "&dLets a PVP player hand-mine blocks",
                        "&dinside another PVP player's claim.",
                        "",
                        "&8Cannot be obtained from villagers."
                )
                .build();
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
        meta.getPersistentDataContainer().set(plugin.getClaimBreakerKey(), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    // Extremely rare chance to inject the Claim Breaker pickaxe into generated structure loot.
    @EventHandler
    public void onLootGenerate(LootGenerateEvent event) {
        double chance = plugin.getConfigManager().getClaimbreakerLootChance();
        if (chance <= 0) return;
        if (random.nextDouble() > chance) return;

        List<ItemStack> loot = event.getLoot();
        loot.add(buildClaimBreakerPickaxe(plugin));
    }

    // Safety net: never allow the Claim Breaker pickaxe to be offered in villager trades,
    // even if a datapack or another plugin tries to add it.
    @EventHandler
    public void onVillagerTrade(VillagerAcquireTradeEvent event) {
        ItemStack result = event.getRecipe().getResult();
        if (result.hasItemMeta() && result.getItemMeta().getPersistentDataContainer()
                .has(plugin.getClaimBreakerKey(), PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }
}
