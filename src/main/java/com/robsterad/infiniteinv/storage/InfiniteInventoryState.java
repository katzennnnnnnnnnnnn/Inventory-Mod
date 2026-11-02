package com.robsterad.infiniteinv.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.robsterad.infiniteinv.inventory.ItemKey;
import com.robsterad.infiniteinv.inventory.PlayerInfiniteInventory;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

public class InfiniteInventoryState extends SavedData {

    public HashMap<UUID, PlayerInfiniteInventory> players = new HashMap<>();
    public HashMap<UUID, Map<ItemKey, Long>> itemTimestamps = new HashMap<>();

    private record SavedItem(ItemStack stack, long count, long timestamp) {}

    private static final Codec<SavedItem> SAVED_ITEM_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.CODEC.fieldOf("item").forGetter(SavedItem::stack),
            Codec.LONG.fieldOf("count").forGetter(SavedItem::count),
            Codec.LONG.fieldOf("ts").forGetter(SavedItem::timestamp)
    ).apply(instance, SavedItem::new));

    private static final Codec<PlayerStateWrapper> PLAYER_STATE_CODEC = SAVED_ITEM_CODEC.listOf().xmap(
            list -> {
                PlayerStateWrapper wrapper = new PlayerStateWrapper(new PlayerInfiniteInventory(), new HashMap<>());
                for (SavedItem saved : list) {
                    ItemKey key = ItemKey.of(saved.stack());
                    if (key != null) {
                        wrapper.inv.setItemCountDirectly(key, saved.count());
                        wrapper.times.put(key, saved.timestamp());
                    }
                }
                return wrapper;
            },
            wrapper -> {
                List<SavedItem> list = new ArrayList<>();
                for (Map.Entry<ItemKey, Long> entry : wrapper.inv.getAllItems().entrySet()) {
                    long ts = wrapper.times.getOrDefault(entry.getKey(), 0L);
                    list.add(new SavedItem(entry.getKey().toStack(1), entry.getValue(), ts));
                }
                return list;
            }
    );

    private record PlayerStateWrapper(PlayerInfiniteInventory inv, Map<ItemKey, Long> times) {}

    public static final Codec<InfiniteInventoryState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, PLAYER_STATE_CODEC).fieldOf("players").forGetter(state -> {
                Map<UUID, PlayerStateWrapper> map = new HashMap<>();
                state.players.forEach((uuid, inv) -> map.put(uuid, new PlayerStateWrapper(inv, state.itemTimestamps.getOrDefault(uuid, new HashMap<>()))));
                return map;
            })
    ).apply(instance, map -> {
        InfiniteInventoryState state = new InfiniteInventoryState();
        map.forEach((uuid, wrapper) -> {
            state.players.put(uuid, wrapper.inv);
            state.itemTimestamps.put(uuid, wrapper.times);
        });
        return state;
    }));

    public static final SavedDataType<InfiniteInventoryState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("infinite-inventory", "infinite_inventory"),
            InfiniteInventoryState::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    public static InfiniteInventoryState getServerState(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public static PlayerInfiniteInventory getPlayerState(ServerPlayer player) {
        InfiniteInventoryState state = getServerState(player.level().getServer());
        return state.players.computeIfAbsent(player.getUUID(), uuid -> new PlayerInfiniteInventory());
    }

    public static void updateTimestamp(ServerPlayer player, ItemKey key) {
        InfiniteInventoryState state = getServerState(player.level().getServer());
        state.itemTimestamps.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).put(key, System.currentTimeMillis());
        state.setDirty();
    }
}
