package dev.virtualkeys.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.virtualkeys.VirtualKeyDefinition;
import dev.virtualkeys.client.VirtualKeyScreen;
import dev.virtualkeys.client.VirtualKeysClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ToggleKeyMapping;
import org.lwjgl.sdl.SDLMouse;
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
            // 1. ToggleKeyMapping（トグルダッシュなど）でトグルON（isDown == true）になっている場合は維持
            if ((Object) this instanceof ToggleKeyMapping) {
                if (this.isDown) {
                    cir.setReturnValue(true);
                    return;
                }
            }

            // 2. 仮想キー（GUI上のボタンクリックで押下された仮想キー）の場合
            if (this.key != null && this.key.getType() == InputConstants.Type.KEYBOARD) {
                int code = this.key.getValue();
                if (VirtualKeysClient.isVirtualKeyDown(code)) {
                    cir.setReturnValue(true);
                    return;
                }
            }

            // 3. 物理キーが押されている場合（歩きながらパネルを開いた際や、移動キーWASD・ジャンプ・ホールドダッシュ）
            if (this.key != null) {
                int code = this.key.getValue();
                if (code != InputConstants.UNKNOWN.getValue()) {
                    if (this.key.getType() == InputConstants.Type.KEYBOARD) {
                        if (VirtualKeyDefinition.getByCode(code) == null && code >= 0 && code < 512 && InputConstants.isKeyDown(code)) {
                            cir.setReturnValue(true);
                            return;
                        }
                    } else if (this.key.getType() == InputConstants.Type.MOUSE) {
                        if (code >= 1 && code <= 32) {
                            int mouseState = SDLMouse.SDL_GetMouseState(null, null);
                            if ((mouseState & (1 << (code - 1))) != 0) {
                                cir.setReturnValue(true);
                                return;
                            }
                        }
                    }
                }
            }
        }
    }
}
