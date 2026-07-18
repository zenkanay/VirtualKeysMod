package dev.virtualkeys.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.virtualkeys.VirtualKeyDefinition;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InputConstants.Key.class)
public class InputConstantsKeyMixin {

    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    private void onGetDisplayName(CallbackInfoReturnable<Component> cir) {
        Object self = this;
        if (self instanceof InputConstants.Key) {
            int val = ((InputConstants.Key) self).getValue();
            VirtualKeyDefinition def = VirtualKeyDefinition.getByCode(val);
            if (def != null) {
                cir.setReturnValue(Component.literal(def.label));
            }
        }
    }
}
