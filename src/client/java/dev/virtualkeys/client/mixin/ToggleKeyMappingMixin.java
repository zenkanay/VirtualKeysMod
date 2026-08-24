package dev.virtualkeys.client.mixin;

import dev.virtualkeys.client.VirtualKeysClient;
import dev.virtualkeys.client.VirtualKeyScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ToggleKeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ToggleKeyMapping.class)
public class ToggleKeyMappingMixin {

    @Inject(method = "release", at = @At("HEAD"), cancellable = true)
    private void onRelease(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof VirtualKeyScreen || VirtualKeysClient.overlayActive) {
            // パネル表示時にトグルダッシュ/トグルスニークがリセットされるのを防ぐ
            ci.cancel();
        }
    }
}
