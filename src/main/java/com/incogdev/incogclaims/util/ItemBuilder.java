package com.incogdev.incogclaims.util;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.List;

public class ItemBuilder {

    private final ItemStack stack;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.stack = new ItemStack(material);
        this.meta = stack.getItemMeta();
    }

    public ItemBuilder name(String name) {
        meta.setDisplayName(Msg.color(name));
        return this;
    }

    public ItemBuilder lore(String... lines) {
        List<String> lore = new ArrayList<>();
        for (String line : lines) lore.add(Msg.color(line));
        meta.setLore(lore);
        return this;
    }

    public ItemBuilder amount(int amount) {
        stack.setAmount(amount);
        return this;
    }

    public ItemBuilder glow() {
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        return this;
    }

    public ItemBuilder tag(NamespacedKey key, String value) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(key, PersistentDataType.STRING, value);
        return this;
    }

    public ItemStack build() {
        stack.setItemMeta(meta);
        return stack;
    }
}
