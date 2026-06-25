package dev.virtualkeys.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyMapping.class)
public class KeyMappingMixin {

    @Shadow
    protected InputConstants.Key key;

    @Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
    private void onIsDown(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        boolean isEditing = (mc.screen instanceof dev.virtualkeys.client.VirtualKeyScreen && ((dev.virtualkeys.client.VirtualKeyScreen) mc.screen).isEditingText())
                || (dev.virtualkeys.client.VirtualKeysClient.overlayActive && dev.virtualkeys.client.VirtualKeysClient.overlayScreen != null && dev.virtualkeys.client.VirtualKeysClient.overlayScreen.isEditingText());
        if (isEditing) {
            cir.setReturnValue(false);
            return;
        }

        if (mc.screen instanceof dev.virtualkeys.client.VirtualKeyScreen || dev.virtualkeys.client.VirtualKeysClient.overlayActive) {
            if (this.key != null && this.key.getType() == InputConstants.Type.KEYSYM) {
                int code = this.key.getValue();
                if (code != InputConstants.UNKNOWN.getValue()) {
                    cir.setReturnValue(InputConstants.isKeyDown(mc.getWindow(), code));
                }
            }
        }
    }
}
