package com.robsterad.infiniteinv.inventory;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public record ItemKey(Item item, DataComponentPatch components) {

    public static ItemKey of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        return new ItemKey(stack.getItem(), stack.getComponentsPatch());
    }

    public ItemStack toStack(int count) {
        ItemStack stack = new ItemStack(this.item, count);
        stack.applyComponents(this.components);
        return stack;
    }
}
