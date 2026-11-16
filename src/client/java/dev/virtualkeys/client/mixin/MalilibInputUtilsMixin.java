package dev.virtualkeys.client.mixin;

import dev.virtualkeys.VirtualKeyDefinition;
import dev.virtualkeys.client.VirtualKeysClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "fi.dy.masa.malilib.util.input.InputUtils", remap = false)
public class MalilibInputUtilsMixin {

    @Inject(method = "isKeyHeld", at = @At("HEAD"), cancellable = true)
    private static void onIsKeyHeld(int scanCode, CallbackInfoReturnable<Boolean> cir) {
        if (VirtualKeysClient.isVirtualKeyDown(scanCode)) {
            cir.setReturnValue(true);
        } else if (VirtualKeyDefinition.getByCode(scanCode) != null || scanCode < 0 || scanCode >= 512) {
            cir.setReturnValue(false);
        }
    }
}
