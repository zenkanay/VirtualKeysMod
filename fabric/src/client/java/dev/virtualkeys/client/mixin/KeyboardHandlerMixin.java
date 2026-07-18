package dev.virtualkeys.client.mixin;

import dev.virtualkeys.client.VirtualKeysClient;
import dev.virtualkeys.client.VirtualKeyScreen;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void onKeyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        
        boolean isEditing = (mc.screen instanceof VirtualKeyScreen && ((VirtualKeyScreen) mc.screen).isEditingText())
                || (VirtualKeysClient.overlayActive && VirtualKeysClient.overlayScreen != null && VirtualKeysClient.overlayScreen.isEditingText());

        if (VirtualKeysClient.openGuiKey != null && VirtualKeysClient.openGuiKey.matches(event)) {
            if (isEditing) {
                // Bypass hotkey logic when editing key labels to allow using these keys for typing
            } else {
                if (VirtualKeysClient.isTypingInTextField()) {
                    // Do NOT cancel the event, just return to allow the character to be typed in the text field
                    return;
                }
                if (action == GLFW.GLFW_PRESS) {
                    if (mc.screen instanceof VirtualKeyScreen) {
                        ((VirtualKeyScreen) mc.screen).closePanel();
                    } else if (VirtualKeysClient.overlayActive) {
                        VirtualKeysClient.closeOverlay();
                    } else {
                        if (mc.screen != null) {
                            VirtualKeysClient.openOverlay(mc.screen);
                        } else {
                            mc.setScreen(new VirtualKeyScreen(null));
                        }
                    }
                }
                ci.cancel();
                return;
            }
        }

        // 常に開くホットキー（入力中でもGUIを開く）
        if (VirtualKeysClient.alwaysOpenGuiKey != null && VirtualKeysClient.alwaysOpenGuiKey.matches(event)) {
            if (isEditing) {
                // Bypass hotkey logic when editing key labels to allow using these keys for typing
            } else {
                if (action == GLFW.GLFW_PRESS) {
                    if (mc.screen instanceof VirtualKeyScreen) {
                        ((VirtualKeyScreen) mc.screen).closePanel();
                    } else if (VirtualKeysClient.overlayActive) {
                        VirtualKeysClient.closeOverlay();
                    } else {
                        if (mc.screen != null) {
                            VirtualKeysClient.openOverlay(mc.screen);
                        } else {
                            mc.setScreen(new VirtualKeyScreen(null));
                        }
                    }
                }
                ci.cancel();
                return;
            }
        }

        // オーバーレイ表示中は PRESS/REPEAT のみ overlayScreen に転送（RELEASE は無視）
        if (VirtualKeysClient.overlayActive && VirtualKeysClient.overlayScreen != null) {
            if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
                if (VirtualKeysClient.overlayScreen.keyPressed(event)) {
                    ci.cancel();
                }
            }
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void onCharTyped(long window, CharacterEvent event, CallbackInfo ci) {
        if (VirtualKeysClient.overlayActive && VirtualKeysClient.overlayScreen != null) {
            if (VirtualKeysClient.overlayScreen.charTyped(event)) {
                ci.cancel();
            }
        }
    }
}
