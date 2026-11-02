package com.robsterad.infiniteinv;

import com.robsterad.infiniteinv.gui.InfiniteInventoryOverlay;
import com.robsterad.infiniteinv.network.SyncInventoryPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

@Environment(EnvType.CLIENT)
public class InfiniteInvClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        InfiniteInventoryOverlay.register();

        ClientPlayNetworking.registerGlobalReceiver(SyncInventoryPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                InfiniteInventoryOverlay.cachedItems = payload.items();
            });
        });
    }
}
