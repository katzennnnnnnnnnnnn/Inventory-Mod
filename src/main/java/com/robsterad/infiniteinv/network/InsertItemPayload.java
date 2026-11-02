package com.robsterad.infiniteinv.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record InsertItemPayload(int slotId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<InsertItemPayload> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("infinite-inventory", "insert_item"));

    @Override
    public CustomPacketPayload.Type<InsertItemPayload> type() {
        return ID;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, InsertItemPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, InsertItemPayload::slotId,
            InsertItemPayload::new
    );
}
