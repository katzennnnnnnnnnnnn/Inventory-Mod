package com.robsterad.infiniteinv.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * @param stackToMatch the item type to take out of the infinite inventory
 * @param all          false = one stack (original behaviour), true = as many as fit into the player's inventory
 */
public record ExtractItemPayload(ItemStack stackToMatch, boolean all) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ExtractItemPayload> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("infinite-inventory", "extract_item"));

    @Override
    public CustomPacketPayload.Type<ExtractItemPayload> type() {
        return ID;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ExtractItemPayload> CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, ExtractItemPayload::stackToMatch,
            ByteBufCodecs.BOOL, ExtractItemPayload::all,
            ExtractItemPayload::new
    );
}
