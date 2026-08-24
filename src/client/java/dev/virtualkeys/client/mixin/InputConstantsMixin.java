package dev.virtualkeys.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.virtualkeys.client.VirtualKeysClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InputConstants.class)
public class InputConstantsMixin {

    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true)
    private static void onIsKeyDown(com.mojang.blaze3d.platform.Window window, int key, CallbackInfoReturnable<Boolean> cir) {
        if (VirtualKeysClient.isVirtualKeyDown(key)) {
            cir.setReturnValue(true);
        }
    }
}
