package dev.virtualkeys.client.mixin;

import dev.virtualkeys.client.VirtualKeysClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Shadow @Final private Minecraft minecraft;

    @Shadow public abstract double getScaledXPos(com.mojang.blaze3d.platform.Window window);
    @Shadow public abstract double getScaledYPos(com.mojang.blaze3d.platform.Window window);

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void onOnButton(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (VirtualKeysClient.overlayActive && VirtualKeysClient.overlayScreen != null) {
            double mx = this.getScaledXPos(this.minecraft.getWindow());
            double my = this.getScaledYPos(this.minecraft.getWindow());

            if (action == 1) { // GLFW_PRESS = 1
                if (VirtualKeysClient.overlayScreen.isMouseOverPanel(mx, my)) {
                    VirtualKeysClient.overlayScreen.handleMouseClick(mx, my, info.button(), action);
                    VirtualKeysClient.overlayCapturedPress = true;
                    ci.cancel();
                }
            } else if (action == 0) { // GLFW_RELEASE = 0
                if (VirtualKeysClient.overlayCapturedPress) {
                    VirtualKeysClient.overlayScreen.handleMouseRelease(mx, my, info.button());
                    VirtualKeysClient.overlayCapturedPress = false;
                    ci.cancel();
                } else if (VirtualKeysClient.overlayScreen.isMouseOverPanel(mx, my)) {
                    VirtualKeysClient.overlayScreen.handleMouseRelease(mx, my, info.button());
                }
            }
        } else {
            // If overlay is not active but the press flag was somehow left true, clear it
            VirtualKeysClient.overlayCapturedPress = false;
        }
    }
}
