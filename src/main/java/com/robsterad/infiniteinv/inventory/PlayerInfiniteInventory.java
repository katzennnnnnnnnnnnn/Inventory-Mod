package com.robsterad.infiniteinv.inventory;

import net.minecraft.world.item.ItemStack;
import java.util.HashMap;
import java.util.Map;

public class PlayerInfiniteInventory {

    private final Map<ItemKey, Long> items = new HashMap<>();

    public void addStack(ItemStack stack) {
        if (stack.isEmpty()) return;

        ItemKey key = ItemKey.of(stack);
        if (key == null) return;

        long currentAmount = this.items.getOrDefault(key, 0L);
        this.items.put(key, currentAmount + stack.getCount());
    }

    public ItemStack removeStack(ItemKey key, int maxCount) {
        if (!this.items.containsKey(key)) {
            return ItemStack.EMPTY;
        }

        long currentAmount = this.items.get(key);
        int amountToTake = (int) Math.min(currentAmount, maxCount);

        if (currentAmount - amountToTake <= 0) {
            this.items.remove(key);
        } else {
            this.items.put(key, currentAmount - amountToTake);
        }

        return key.toStack(amountToTake);
    }

    public Map<ItemKey, Long> getAllItems() {
        return new HashMap<>(this.items);
    }

    public void clear() {
        this.items.clear();
    }

    public void setItemCountDirectly(ItemKey key, long count) {
        this.items.put(key, count);
    }
}
