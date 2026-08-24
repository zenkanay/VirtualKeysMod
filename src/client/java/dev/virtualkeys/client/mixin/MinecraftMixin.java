package dev.virtualkeys.client.mixin;

import dev.virtualkeys.client.VirtualKeysClient;
import dev.virtualkeys.client.VirtualKeyScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        if (VirtualKeysClient.overlayActive && VirtualKeysClient.overlayScreen != null) {
            // If the active screen is closed (null) or changed to something other than the VirtualKeysScreen
            // itself or the parent screen it was overlaying, close the overlay to keep the state synchronized.
            if (screen == null || (screen != VirtualKeysClient.overlayScreen && screen != VirtualKeysClient.overlayScreen.getParentScreen())) {
                VirtualKeysClient.closeOverlay();
            }
        }
    }
}
