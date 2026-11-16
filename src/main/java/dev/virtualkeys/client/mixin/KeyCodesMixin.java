package dev.virtualkeys.client.mixin;
 
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.virtualkeys.VirtualKeyDefinition;
 
@Mixin(value = fi.dy.masa.malilib.util.KeyCodes.class, remap = false)
public class KeyCodesMixin {
 
    @Inject(method = "getNameForKey", at = @At("HEAD"), cancellable = true)
    private static void onGetNameForKey(int key, CallbackInfoReturnable<String> cir) {
        VirtualKeyDefinition def = VirtualKeyDefinition.getByCode(key);
        if (def != null) {
            cir.setReturnValue(def.label);
        }
    }

    @Inject(method = "getKeyCodeFromName", at = @At("RETURN"), cancellable = true)
    private static void onGetKeyCodeFromName(String name, CallbackInfoReturnable<Integer> cir) {
        int ret = cir.getReturnValueI();
        if (ret == -1 || ret == 0) {
            Integer code = VirtualKeyDefinition.getCodeByName(name);
            if (code != null) {
                cir.setReturnValue(code);
            }
        }
    }
}
