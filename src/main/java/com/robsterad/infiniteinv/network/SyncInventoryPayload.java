package com.robsterad.infiniteinv.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record SyncInventoryPayload(List<NetworkItemData> items) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncInventoryPayload> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("infinite-inventory", "sync_inventory"));

    public record NetworkItemData(ItemStack stack, long count, long timestamp) {
        public static final StreamCodec<RegistryFriendlyByteBuf, NetworkItemData> CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC, NetworkItemData::stack,
                ByteBufCodecs.VAR_LONG, NetworkItemData::count,
                ByteBufCodecs.VAR_LONG, NetworkItemData::timestamp,
                NetworkItemData::new
        );
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncInventoryPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, NetworkItemData.CODEC), SyncInventoryPayload::items,
            SyncInventoryPayload::new
    );

    @Override
    public CustomPacketPayload.Type<SyncInventoryPayload> type() {
        return ID;
    }
}
