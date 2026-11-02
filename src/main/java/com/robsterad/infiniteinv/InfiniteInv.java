package com.robsterad.infiniteinv;

import com.robsterad.infiniteinv.inventory.ItemKey;
import com.robsterad.infiniteinv.inventory.PlayerInfiniteInventory;
import com.robsterad.infiniteinv.network.ExtractItemPayload;
import com.robsterad.infiniteinv.network.InsertItemPayload;
import com.robsterad.infiniteinv.network.SyncInventoryPayload;
import com.robsterad.infiniteinv.storage.InfiniteInventoryState;
import com.robsterad.infiniteinv.storage.LegacyDataMigration;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InfiniteInv implements ModInitializer {

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(SyncInventoryPayload.ID, SyncInventoryPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ExtractItemPayload.ID, ExtractItemPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(InsertItemPayload.ID, InsertItemPayload.CODEC);

        ServerLifecycleEvents.SERVER_STARTED.register(LegacyDataMigration::migrate);

        // INSERT (SHIFT + CLICK)
        ServerPlayNetworking.registerGlobalReceiver(InsertItemPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                AbstractContainerMenu handler = player.containerMenu;
                int slotId = payload.slotId();

                if (slotId >= 0 && slotId < handler.slots.size()) {
                    Slot slot = handler.slots.get(slotId);

                    if (slot.hasItem()) {
                        ItemStack stack = slot.getItem();
                        try {
                            PlayerInfiniteInventory inv = InfiniteInventoryState.getPlayerState(player);
                            inv.addStack(stack);
                            slot.set(ItemStack.EMPTY);

                            // save timestamp for "recent" sort
                            ItemKey key = ItemKey.of(stack);
                            if (key != null) {
                                InfiniteInventoryState.updateTimestamp(player, key);
                            }

                            InfiniteInventoryState.getServerState(context.server()).setDirty();
                            syncInventoryToClient(player);
                        } catch (Exception e) {
                            System.err.println("Error at adding: " + e.getMessage());
                        }
                    }
                }
            });
        });

        // EXTRACT (CLICK ON ITEM IN UI)
        ServerPlayNetworking.registerGlobalReceiver(ExtractItemPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                ItemStack requestedStack = payload.stackToMatch();
                PlayerInfiniteInventory inv = InfiniteInventoryState.getPlayerState(player);
                ItemKey key = ItemKey.of(requestedStack);

                if (key != null && inv.getAllItems().containsKey(key)) {
                    int maxTake = requestedStack.getMaxStackSize();
                    boolean extractAll = payload.all();
                    boolean removedAny = false;

                    while (true) {
                        ItemStack extracted = inv.removeStack(key, maxTake);
                        if (extracted.isEmpty()) break;
                        removedAny = true;

                        player.getInventory().add(extracted);
                        if (extracted.getCount() > 0) {
                            if (extractAll) {
                                // inventory is full: put the remainder back instead of dropping it on the ground
                                inv.addStack(extracted);
                            } else {
                                player.drop(extracted, false);
                            }
                            break;
                        }
                        if (!extractAll) break;
                    }

                    if (removedAny) {
                        InfiniteInventoryState.getServerState(context.server()).setDirty();
                        syncInventoryToClient(player);
                    }
                }
            });
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            syncInventoryToClient(handler.player);
        });
    }

    public static void syncInventoryToClient(ServerPlayer player) {
        PlayerInfiniteInventory inv = InfiniteInventoryState.getPlayerState(player);
        InfiniteInventoryState state = InfiniteInventoryState.getServerState(player.level().getServer());
        Map<ItemKey, Long> timestamps = state.itemTimestamps.getOrDefault(player.getUUID(), new HashMap<>());

        List<SyncInventoryPayload.NetworkItemData> list = new ArrayList<>();
        for (Map.Entry<ItemKey, Long> entry : inv.getAllItems().entrySet()) {
            list.add(new SyncInventoryPayload.NetworkItemData(
                    entry.getKey().toStack(1),
                    entry.getValue(),
                    timestamps.getOrDefault(entry.getKey(), 0L)
            ));
        }
        ServerPlayNetworking.send(player, new SyncInventoryPayload(list));
    }
}
