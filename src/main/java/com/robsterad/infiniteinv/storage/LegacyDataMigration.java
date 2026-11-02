package com.robsterad.infiniteinv.storage;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LegacyDataMigration {

    // 1.21.8 stored data as <world>/data/infinite_inventory.dat (flat, no namespace subdir)
    private static final String OLD_FILE = "infinite_inventory.dat";
    // 26.1.2 stores as <world>/data/infinite-inventory/infinite_inventory.dat
    private static final String NEW_FILE = "infinite-inventory/infinite_inventory.dat";

    public static void migrate(MinecraftServer server) {
        Path dataDir = server.getWorldPath(LevelResource.ROOT).resolve("data");
        Path oldFile = dataDir.resolve(OLD_FILE);
        if (!Files.exists(oldFile)) return;

        Path newFile = dataDir.resolve(NEW_FILE);
        if (Files.exists(newFile)) {
            // World already saved in the new format; old file is stale, remove it.
            tryDelete(oldFile);
            return;
        }

        try {
            CompoundTag root = NbtIo.readCompressed(oldFile, NbtAccounter.unlimitedHeap());
            // Saved-data files are wrapped: { DataVersion: X, data: { ... codec payload ... } }
            Tag dataTag = root.contains("data") ? root.get("data") : root;

            DynamicOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess());
            DataResult<InfiniteInventoryState> parseResult =
                    InfiniteInventoryState.CODEC.parse(ops, dataTag);

            parseResult.result().ifPresent(migrated -> {
                InfiniteInventoryState current = InfiniteInventoryState.getServerState(server);
                // putIfAbsent so we never overwrite data the player already has in the new format
                migrated.players.forEach((uuid, inv) ->
                        current.players.putIfAbsent(uuid, inv));
                migrated.itemTimestamps.forEach((uuid, timestamps) ->
                        current.itemTimestamps.putIfAbsent(uuid, timestamps));
                current.setDirty();
                // Old file is intentionally kept until the next startup confirms the new file exists.
                // This makes the migration crash-safe: if the server stops before the world saves,
                // the migration simply runs again on the next startup.
                System.out.println("[InfiniteInv] Migrated legacy 1.21.8 save data. " +
                        "The old file will be cleaned up on the next startup once the world has saved.");
            });

            parseResult.error().ifPresent(err ->
                    System.err.println("[InfiniteInv] Could not parse legacy save " +
                            "(item encoding may be incompatible between 1.21.8 and 26.1.2). " +
                            "The old file has been left at: " + oldFile));

        } catch (IOException e) {
            System.err.println("[InfiniteInv] Failed to read legacy save file: " + e.getMessage());
        }
    }

    private static void tryDelete(Path path) {
        try {
            Files.delete(path);
        } catch (IOException e) {
            System.err.println("[InfiniteInv] Could not delete legacy save file at " + path + ": " + e.getMessage());
        }
    }
}
