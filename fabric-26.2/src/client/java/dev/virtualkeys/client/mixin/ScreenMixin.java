package dev.virtualkeys.client.mixin;

import dev.virtualkeys.client.VirtualKeysClient;
import dev.virtualkeys.client.VirtualKeyScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ScreenMixin {

    @Inject(method = "extractBlurredBackground", at = @At("HEAD"), cancellable = true)
    private void onExtractBlurredBackground(GuiGraphicsExtractor guiGraphicsExtractor, CallbackInfo ci) {
        if (Minecraft.getInstance().gui.screen() instanceof VirtualKeyScreen || VirtualKeysClient.overlayActive) {
            ci.cancel();
        }
    }

    @Inject(method = "extractTransparentBackground", at = @At("HEAD"), cancellable = true)
    private void onExtractTransparentBackground(GuiGraphicsExtractor guiGraphicsExtractor, CallbackInfo ci) {
        if (Minecraft.getInstance().gui.screen() instanceof VirtualKeyScreen || VirtualKeysClient.overlayActive) {
            ci.cancel();
        }
    }

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("TAIL"))
    private void onExtractRenderStateWithTooltipAndSubtitles(GuiGraphicsExtractor guiGraphicsExtractor, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (VirtualKeysClient.overlayActive && VirtualKeysClient.overlayScreen != null && (Object)this != VirtualKeysClient.overlayScreen) {
            int w = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            int h = Minecraft.getInstance().getWindow().getGuiScaledHeight();
            if (VirtualKeysClient.overlayScreen.width != w || VirtualKeysClient.overlayScreen.height != h) {
                VirtualKeysClient.overlayScreen.init(w, h);
            }
            VirtualKeysClient.overlayScreen.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, delta);
        }
    }
}
