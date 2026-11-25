package com.robsterad.infiniteinv.mixin;

import com.robsterad.infiniteinv.PrinterRestock;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Hooks the material request of Litematica Printer (Hana). {@link Pseudo} makes this a no-op when the
 * printer is not installed, and {@code require = 0} keeps the game running if a printer version
 * does not have a method. Two entry points are hooked because printer versions differ:
 * <ul>
 *   <li>the public {@code request(List, Item, Source)}: present in all versions (Beta2.x, 3.x)</li>
 *   <li>the private {@code request(List, Item, int, Source, boolean)}: newer versions (Beta3.3+),
 *       reached directly by requests that carry a minimum count</li>
 * </ul>
 * Calling both for one request is harmless, {@link PrinterRestock} has a per-item cooldown.
 */
@Pseudo
@Mixin(targets = "me.aleksilassila.litematica.printer.integration.inventory.MaterialRequestCoordinator")
public abstract class PrinterMaterialRequestMixin {

    @Inject(
            method = "request(Ljava/util/List;Lnet/minecraft/world/item/Item;Lme/aleksilassila/litematica/printer/integration/inventory/MaterialRequest$Source;)Lme/aleksilassila/litematica/printer/integration/inventory/MaterialReservation;",
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private void infiniteInv$restockSimple(List<Item> acceptedItems, Item preferredItem,
                                           @Coerce Object source,
                                           CallbackInfoReturnable<Object> cir) {
        PrinterRestock.onMaterialRequested(acceptedItems, 1, false);
    }

    @Inject(
            method = "request(Ljava/util/List;Lnet/minecraft/world/item/Item;ILme/aleksilassila/litematica/printer/integration/inventory/MaterialRequest$Source;Z)Lme/aleksilassila/litematica/printer/integration/inventory/MaterialReservation;",
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private void infiniteInv$restockWithCount(List<Item> acceptedItems, Item preferredItem, int minimumCount,
                                              @Coerce Object source, boolean emptyShulkerOnly,
                                              CallbackInfoReturnable<Object> cir) {
        PrinterRestock.onMaterialRequested(acceptedItems, minimumCount, emptyShulkerOnly);
    }
}
