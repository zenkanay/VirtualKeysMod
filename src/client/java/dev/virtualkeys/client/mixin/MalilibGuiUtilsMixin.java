package dev.virtualkeys.client.mixin;

import dev.virtualkeys.client.VirtualKeyScreen;
import dev.virtualkeys.client.VirtualKeysClient;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "fi.dy.masa.malilib.util.GuiUtils", remap = false)
public class MalilibGuiUtilsMixin {

    @Inject(method = "getCurrentScreen", at = @At("RETURN"), cancellable = true)
    private static void onGetCurrentScreen(CallbackInfoReturnable<Screen> cir) {
        if (VirtualKeysClient.simulatingVirtualKey && cir.getReturnValue() instanceof VirtualKeyScreen) {
            cir.setReturnValue(null);
        }
    }
}
