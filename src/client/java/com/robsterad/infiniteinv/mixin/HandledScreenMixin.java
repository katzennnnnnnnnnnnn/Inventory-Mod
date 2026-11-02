package com.robsterad.infiniteinv.mixin;

import com.robsterad.infiniteinv.gui.InfiniteInventoryOverlay;
import com.robsterad.infiniteinv.network.InsertItemPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenMixin {

    @Shadow protected Slot hoveredSlot;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void interceptShiftClick(MouseButtonEvent event, boolean bl, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof InventoryScreen) {
            if (!InfiniteInventoryOverlay.panelVisible) return;

            if ((InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), InputConstants.KEY_LSHIFT)
                    || InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), InputConstants.KEY_RSHIFT))
                    && event.button() == 0) {
                if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
                    ClientPlayNetworking.send(new InsertItemPayload(this.hoveredSlot.index));
                    cir.setReturnValue(true);
                }
            }
        }
    }

    /**
     * Shift + F: move the stack under the mouse into the infinite inventory.
     * Ctrl  + F: move every stack of that item (main inventory + hotbar) into it.
     */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void infiniteInv$insertWithKey(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof InventoryScreen)) return;
        if (!InfiniteInventoryOverlay.panelVisible) return;
        if (event.key() != GLFW.GLFW_KEY_F) return;
        if (InfiniteInventoryOverlay.isSearchFocused()) return;

        Slot hovered = this.hoveredSlot;
        if (hovered == null || !hovered.hasItem()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean ctrl = InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_LCONTROL)
                || InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_RCONTROL);
        boolean shift = InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_LSHIFT)
                || InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_RSHIFT);

        if (ctrl) {
            ItemStack wanted = hovered.getItem();
            for (Slot slot : mc.player.containerMenu.slots) {
                // menu slots 9-35 = main inventory, 36-44 = hotbar (no armor, crafting grid or offhand)
                if (slot.index < 9 || slot.index > 44) continue;
                if (slot.hasItem() && ItemStack.isSameItemSameComponents(slot.getItem(), wanted)) {
                    ClientPlayNetworking.send(new InsertItemPayload(slot.index));
                }
            }
            cir.setReturnValue(true);
        } else if (shift) {
            ClientPlayNetworking.send(new InsertItemPayload(hovered.index));
            cir.setReturnValue(true);
        }
    }
}
