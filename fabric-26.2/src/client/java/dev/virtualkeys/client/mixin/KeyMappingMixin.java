package dev.virtualkeys.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.virtualkeys.client.VirtualKeysClient;
import dev.virtualkeys.client.VirtualKeyScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyMapping.class)
public class KeyMappingMixin {

    @Shadow
    protected InputConstants.Key key;

    @Shadow
    private boolean isDown;

    @Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
    private void onIsDown(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        boolean isEditing = (mc.gui.screen() instanceof VirtualKeyScreen && ((VirtualKeyScreen) mc.gui.screen()).isEditingText())
                || (VirtualKeysClient.overlayActive && VirtualKeysClient.overlayScreen != null && VirtualKeysClient.overlayScreen.isEditingText());
        if (isEditing) {
            cir.setReturnValue(false);
            return;
        }

        if (mc.gui.screen() instanceof VirtualKeyScreen || VirtualKeysClient.overlayActive) {
            // トグルダッシュ等で isDown フィールドが true の場合は維持する
            if (this.isDown) {
                cir.setReturnValue(true);
                return;
            }

            if (this.key != null) {
                int code = this.key.getValue();
                if (code != InputConstants.UNKNOWN.getValue()) {
                    if (this.key.getType() == InputConstants.Type.KEYSYM) {
                        boolean down = InputConstants.isKeyDown(mc.getWindow(), code) || VirtualKeysClient.isVirtualKeyDown(code);
                        if (down) {
                            cir.setReturnValue(true);
                        }
                    } else if (this.key.getType() == InputConstants.Type.MOUSE) {
                        long handle = mc.getWindow().handle();
                        boolean down = GLFW.glfwGetMouseButton(handle, code) == GLFW.GLFW_PRESS;
                        if (down) {
                            cir.setReturnValue(true);
                        }
                    } else if (this.key.getType() == InputConstants.Type.SCANCODE) {
                        boolean down = InputConstants.isKeyDown(mc.getWindow(), code);
                        if (down) {
                            cir.setReturnValue(true);
                        }
                    }
                }
            }
        }
    }
}
