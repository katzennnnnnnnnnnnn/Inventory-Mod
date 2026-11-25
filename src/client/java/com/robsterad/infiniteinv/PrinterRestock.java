package com.robsterad.infiniteinv;

import com.robsterad.infiniteinv.gui.InfiniteInventoryOverlay;
import com.robsterad.infiniteinv.network.ExtractItemPayload;
import com.robsterad.infiniteinv.network.SyncInventoryPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Automatic restock for the Litematica Printer (Hana).
 *
 * <p>The printer calls {@link #onMaterialRequested} every time it needs a material. If the player's
 * inventory does not hold enough of it, one stack is taken out of the infinite inventory. The printer
 * itself retries a moment later and then finds the item in the normal inventory.</p>
 */
public final class PrinterRestock {

    /** Minimum number of game ticks between two automatic requests for the same item. */
    private static final long COOLDOWN_TICKS = 12L;

    private static final Map<Item, Long> LAST_REQUEST = new HashMap<>();

    private PrinterRestock() {
    }

    public static void onMaterialRequested(List<Item> acceptedItems, int minimumCount, boolean emptyShulkerOnly) {
        if (emptyShulkerOnly || acceptedItems == null || acceptedItems.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return;
        if (player.getAbilities().instabuild || player.isSpectator()) return;
        if (!ClientPlayNetworking.canSend(ExtractItemPayload.ID)) return;

        // Enough material already in the normal inventory: nothing to do.
        for (Item item : acceptedItems) {
            if (countInInventory(player, item) >= Math.max(1, minimumCount)) return;
        }

        long now = level.getGameTime();
        for (Item item : acceptedItems) {
            SyncInventoryPayload.NetworkItemData entry = findInInfiniteInventory(item);
            if (entry == null) continue;

            Long last = LAST_REQUEST.get(item);
            if (last != null && now >= last && now - last < COOLDOWN_TICKS) {
                return; // a request for this item is still on its way
            }
            LAST_REQUEST.put(item, now);
            ClientPlayNetworking.send(new ExtractItemPayload(entry.stack(), false, true));
            return;
        }
    }

    private static int countInInventory(LocalPlayer player, Item item) {
        Inventory inventory = player.getInventory();
        int size = Math.min(36, inventory.getContainerSize());
        int count = 0;
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Prefers the plain item (no custom name / enchantments) over modified variants. */
    private static SyncInventoryPayload.NetworkItemData findInInfiniteInventory(Item item) {
        SyncInventoryPayload.NetworkItemData fallback = null;
        for (SyncInventoryPayload.NetworkItemData data : InfiniteInventoryOverlay.cachedItems) {
            if (data.count() <= 0 || !data.stack().is(item)) continue;
            if (data.stack().getComponentsPatch().isEmpty()) {
                return data;
            }
            if (fallback == null) fallback = data;
        }
        return fallback;
    }
}
