package com.robsterad.infiniteinv.mixin;

import com.robsterad.infiniteinv.gui.InfiniteInventoryOverlay;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class ScreenMixin {

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void interceptCharTyped(long window, CharacterEvent event, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen == null) return;
        if (client.screen instanceof InventoryScreen) {
            if (InfiniteInventoryOverlay.onCharTyped(event)) {
                ci.cancel();
            }
        }
    }
}
