package dev.virtualkeys.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.virtualkeys.VirtualKeyDefinition;
import dev.virtualkeys.client.VirtualKeyScreen;
import dev.virtualkeys.client.VirtualKeysClient;
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
        boolean isEditing = (mc.screen instanceof VirtualKeyScreen && ((VirtualKeyScreen) mc.screen).isEditingText())
                || (VirtualKeysClient.overlayActive && VirtualKeysClient.overlayScreen != null && VirtualKeysClient.overlayScreen.isEditingText());
        if (isEditing) {
            cir.setReturnValue(false);
            return;
        }

        if (mc.screen instanceof VirtualKeyScreen || VirtualKeysClient.overlayActive) {
            if (this.key != null && this.key.getType() == InputConstants.Type.KEYSYM) {
                int code = this.key.getValue();
                if (VirtualKeysClient.isVirtualKeyDown(code)) {
                    cir.setReturnValue(true);
                } else if (code != InputConstants.UNKNOWN.getValue() && VirtualKeyDefinition.getByCode(code) == null) {
                    cir.setReturnValue(InputConstants.isKeyDown(mc.getWindow().getWindow(), code));
                }
            }
        }
    }

    @Inject(method = "getTranslatedKeyMessage", at = @At("HEAD"), cancellable = true)
    private void onGetTranslatedKeyMessage(CallbackInfoReturnable<net.minecraft.network.chat.Component> cir) {
        if (this.key != null) {
            VirtualKeyDefinition def = VirtualKeyDefinition.getByCode(this.key.getValue());
            if (def != null) {
                cir.setReturnValue(net.minecraft.network.chat.Component.literal(def.label));
            }
        }
    }
}
