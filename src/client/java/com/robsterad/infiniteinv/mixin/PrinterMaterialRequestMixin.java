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
 * changed this method.
 */
@Pseudo
@Mixin(targets = "me.aleksilassila.litematica.printer.integration.inventory.MaterialRequestCoordinator")
public abstract class PrinterMaterialRequestMixin {

    @Inject(
            method = "request(Ljava/util/List;Lnet/minecraft/world/item/Item;ILme/aleksilassila/litematica/printer/integration/inventory/MaterialRequest$Source;Z)Lme/aleksilassila/litematica/printer/integration/inventory/MaterialReservation;",
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private void infiniteInv$restock(List<Item> acceptedItems, Item preferredItem, int minimumCount,
                                     @Coerce Object source, boolean emptyShulkerOnly,
                                     CallbackInfoReturnable<Object> cir) {
        PrinterRestock.onMaterialRequested(acceptedItems, minimumCount, emptyShulkerOnly);
    }
}
