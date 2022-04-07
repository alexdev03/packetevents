/*
 * This file is part of packetevents - https://github.com/retrooper/packetevents
 * Copyright (C) 2021 retrooper and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.retrooper.packetevents.protocol.item;

import com.github.retrooper.packetevents.protocol.item.enchantment.Enchantment;
import com.github.retrooper.packetevents.protocol.item.enchantment.type.EnchantmentType;
import com.github.retrooper.packetevents.protocol.item.enchantment.type.EnchantmentTypes;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.nbt.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ItemStack {
    public static final ItemStack EMPTY = new ItemStack(ItemTypes.AIR, 0, new NBTCompound(), 0);
    private final ItemType type;
    private int amount;
    @Nullable
    private NBTCompound nbt;
    private int legacyData = -1;

    private boolean cachedIsEmpty = false;

    private ItemStack(ItemType type, int amount, @Nullable NBTCompound nbt, int legacyData) {
        this.type = type;
        this.amount = amount;
        this.nbt = nbt;
        this.legacyData = legacyData;
        updateCachedEmptyStatus();
    }

    public int getMaxStackSize() {
        return getType().getMaxAmount();
    }

    public boolean isStackable() {
        return this.getMaxStackSize() > 1 && (!this.isDamageableItem() || !this.isDamaged());
    }

    public boolean isDamageableItem() {
        if (!this.cachedIsEmpty && this.getType().getMaxDurability() > 0) {
            NBTCompound tag = this.getNBT();
            return tag == null || !tag.getBoolean("Unbreakable");
        } else {
            return false;
        }
    }

    public boolean isDamaged() {
        return this.isDamageableItem() && this.getDamageValue() > 0;
    }

    public int getDamageValue() {
        if (nbt == null) return 0;
        NBTInt damage = this.nbt.getTagOfTypeOrNull("Damage", NBTInt.class);
        return damage == null ? 0 : damage.getAsInt();
    }

    public void setDamageValue(int damage) {
        this.getOrCreateTag().setTag("Damage", new NBTInt(Math.max(0, damage)));
    }

    public int getMaxDamage() {
        return this.getType().getMaxDurability();
    }

    public NBTCompound getOrCreateTag() {
        if (this.nbt == null) {
            this.nbt = new NBTCompound();
        }

        return this.nbt;
    }

    private void updateCachedEmptyStatus() {
        cachedIsEmpty = isEmpty();
    }

    public ItemType getType() {
        return cachedIsEmpty ? ItemTypes.AIR : type;
    }

    public int getAmount() {
        return cachedIsEmpty ? 0 : amount;
    }

    public void shrink(int amount) {
        this.setAmount(this.getAmount() - amount);
    }

    public void grow(int amount) {
        this.setAmount(this.getAmount() + amount);
    }

    public void setAmount(int amount) {
        this.amount = amount;
        updateCachedEmptyStatus();
    }

    public ItemStack split(int toTake) {
        int i = Math.min(toTake, getAmount());
        ItemStack itemstack = this.copy();
        itemstack.setAmount(i);
        this.shrink(i);
        return itemstack;
    }

    public ItemStack copy() {
        return cachedIsEmpty ? EMPTY : new ItemStack(type, amount, nbt == null ? new NBTCompound() : nbt.copy(), legacyData);
    }

    @Nullable
    public NBTCompound getNBT() {
        return nbt;
    }

    public void setNBT(NBTCompound nbt) {
        this.nbt = nbt;
    }

    public int getLegacyData() {
        return legacyData;
    }

    public void setLegacyData(int legacyData) {
        this.legacyData = legacyData;
    }

    public boolean isEnchantable() {
        if (getType() == ItemTypes.BOOK) return getAmount() == 1;
        if (getType() == ItemTypes.ENCHANTED_BOOK) return false;
        return getMaxStackSize() == 1 && canBeDepleted() && !isEnchanted();
    }

    public boolean isEnchanted() {
        if (!isEmpty() && this.nbt != null && this.nbt.getCompoundListTagOrNull("Enchantments") != null) {
            return !this.nbt.getCompoundListTagOrNull("Enchantments").isEmpty();
        } else {
            return false;
        }
    }

    private static List<Enchantment> getEnchantments(@Nullable NBTCompound nbt) {
        if (nbt == null || nbt.getCompoundListTagOrNull("Enchantments") == null)
            return new ArrayList<>(0);
        NBTList<NBTCompound> nbtList = nbt.getCompoundListTagOrNull("Enchantments");
        List<NBTCompound> compounds = nbtList.getTags();
        List<Enchantment> enchantments = new ArrayList<>(compounds.size());
        for (NBTCompound compound : compounds) {
            String id = compound.getStringTagValueOrNull("id");
            EnchantmentType type = EnchantmentTypes.getByName(id);
            if (type != null) {
                NBTShort levelTag = compound.getTagOfTypeOrNull("lvl", NBTShort.class);
                int level = levelTag.getAsInt();
                Enchantment enchantment = Enchantment.builder().type(type).level(level).build();
                enchantments.add(enchantment);
            }
        }
        return enchantments;
    }

    public List<Enchantment> getEnchantments() {
        return getEnchantments(this.nbt);
    }

    // TODO: Test on outdated versions
    public int getEnchantmentLevel(EnchantmentType enchantment) {
        if (isEnchanted()) {
            // isEnchanted() is true, so we can assume that nbt is not null
            assert nbt != null;
            for (NBTCompound base : nbt.getCompoundListTagOrNull("Enchantments").getTags()) {
                NBTString string = base.getTagOfTypeOrNull("id", NBTString.class);
                if (string != null && enchantment == EnchantmentTypes.getByName(string.getValue())) {
                    return base.getTagOfTypeOrNull("lvl", NBTShort.class).getAsInt();
                }
            }
        }

        return 0;
    }

    public void setEnchantments(List<Enchantment> enchantments) {
        nbt = getOrCreateTag(); // Create tag if null
        if (enchantments.isEmpty()) {
            //Let us clear the enchantments
            if (nbt.getTagOrNull("Enchantments") != null) {
                nbt.removeTag("Enchantments");
            }
        }
        else {
            List<NBTCompound> list = new ArrayList<>();
            for (Enchantment enchantment : enchantments) {
                NBTCompound compound = new NBTCompound();
                compound.setTag("id", new NBTString(enchantment.getType().getName().toString()));
                compound.setTag("lvl", new NBTShort((short) enchantment.getLevel()));
                list.add(compound);
            }
            assert nbt != null; // NBT was created in getOrCreateTag()
            nbt.setTag("Enchantments", new NBTList<>(NBTType.COMPOUND, list));
        }
    }

    public boolean canBeDepleted() {
        return this.getType().getMaxDurability() > 0;
    }

    public boolean is(ItemType type) {
        return this.getType() == type;
    }

    public static boolean isSameItemSameTags(ItemStack stack, ItemStack otherStack) {
        return stack.is(otherStack.getType()) && ItemStack.tagMatches(stack, otherStack);
    }

    public static boolean tagMatches(ItemStack left, ItemStack right) {
        return left.isEmpty() && right.isEmpty() || (!left.isEmpty() && !right.isEmpty() && ((left.nbt != null || right.nbt == null) && (left.nbt == null || left.nbt.equals(right.nbt))));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj instanceof ItemStack) {
            ItemStack itemStack = (ItemStack) obj;
            return getType().equals(itemStack.getType())
                    && amount == itemStack.amount
                    && Objects.equals(nbt, itemStack.nbt)
                    && legacyData == itemStack.legacyData;
        }
        return false;
    }

    @Override
    public String toString() {
        if (cachedIsEmpty) {
            return "ItemStack[null]";
        } else {
            String identifier = type == null ? "null" : type.getName().toString();
            int maxAmount = getType().getMaxAmount();
            return "ItemStack[type=" + identifier + ", amount=" + amount + "/" + maxAmount
                    + ", nbt tag names: " + (nbt != null ? nbt.getTagNames() : "[null]") + ", legacyData=" + legacyData + "]";
        }
    }

    public boolean isEmpty() {
        return type == null || type == ItemTypes.AIR || amount <= 0;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ItemType type;
        private int amount = 1;
        private NBTCompound nbt = null;
        private int legacyData = -1;
        private List<Enchantment> enchantments = null;

        public Builder type(ItemType type) {
            this.type = type;
            return this;
        }

        public Builder amount(int amount) {
            this.amount = amount;
            return this;
        }

        public Builder nbt(NBTCompound nbt) {
            this.nbt = nbt;

            List<Enchantment> nbtEnchantments = getEnchantments(nbt);
            // This assumes logically, if already set enchants before NBT, then they would still want the enchants
            if (!nbtEnchantments.isEmpty()) {
                this.enchantments = nbtEnchantments;
            }

            return this;
        }

        public Builder legacyData(int legacyData) {
            this.legacyData = legacyData;
            return this;
        }

        /**
         * Side effect: This will cause NBT to be created if it is null
         * This may affect some client sided inventory operations and may cause a desync
         *
         * @param enchantments The enchantments to set
         * @return This builder
         */
        public Builder enchantments(List<Enchantment> enchantments) {
            this.enchantments = enchantments;
            return this;
        }

        public Builder addEnchantment(Enchantment enchantment) {
            if (enchantments == null) {
                enchantments = new ArrayList<>();
            }
            this.enchantments.add(enchantment);
            return this;
        }

        public ItemStack build() {
            ItemStack stack = new ItemStack(type, amount, nbt, legacyData);
            if (enchantments != null) {
                stack.setEnchantments(enchantments);
            }
            return stack;
        }

    }
}
