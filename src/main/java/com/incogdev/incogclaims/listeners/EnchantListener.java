package com.incogdev.incogclaims.listeners;

import com.incogdev.incogclaims.IncogClaims;
import com.incogdev.incogclaims.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
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

    /** Builds the rare Claim Breaker pickaxe - only tool capable of breaking a claim's core block. */
    public static ItemStack buildClaimBreakerPickaxe(IncogClaims plugin) {
        ItemStack item = new ItemBuilder(Material.NETHERITE_PICKAXE)
                .name("&5&l\u2726 Claim Breaker \u2726")
                .lore(
                        "&7A rare tool said to be forged",
                        "&7by raiders themselves.",
                        "",
                        "&dLets a PVP player break another",
                        "&dPVP player's claim core, ending",
                        "&dtheir claim for good.",
                        "",
                        "&8Only breaks claim cores and TNT-immune",
                        "&8blocks (obsidian, etc). Not bedrock.",
                        "&8Cannot be obtained from villagers."
                )
                .build();
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
        meta.getPersistentDataContainer().set(plugin.getClaimBreakerKey(), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Registers the Claim Breaker's crafting recipe:
     *   N N N   (N = Netherite Ingot)
     *   D P D   (D = Diamond, P = Netherite Pickaxe)
     *   C T C   (C = Copper Ingot, T = Totem of Undying)
     */
    public static void registerRecipe(IncogClaims plugin) {
        if (!plugin.getConfigManager().isClaimbreakerCraftable()) return;

        NamespacedKey key = new NamespacedKey(plugin, "claim_breaker");
        ShapedRecipe recipe = new ShapedRecipe(key, buildClaimBreakerPickaxe(plugin));
        recipe.shape("NNN", "DPD", "CTC");
        recipe.setIngredient('N', Material.NETHERITE_INGOT);
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('P', Material.NETHERITE_PICKAXE);
        recipe.setIngredient('C', Material.COPPER_INGOT);
        recipe.setIngredient('T', Material.TOTEM_OF_UNDYING);

        plugin.getServer().addRecipe(recipe);
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
