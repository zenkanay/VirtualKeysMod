package dev.virtualkeys.client.mixin;

import dev.virtualkeys.VirtualKeyDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "fi.dy.masa.malilib.util.input.ScanCodes", remap = false)
public class ScanCodesMixin {

    @Inject(method = "getNameForScanCode", at = @At("HEAD"), cancellable = true)
    private static void onGetNameForScanCode(int scanCode, CallbackInfoReturnable<String> cir) {
        VirtualKeyDefinition def = VirtualKeyDefinition.getByCode(scanCode);
        if (def != null) {
            cir.setReturnValue(def.label);
        }
    }

    @Inject(method = "getScanCodeFromName", at = @At("RETURN"), cancellable = true)
    private static void onGetScanCodeFromName(String name, CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValueI() == 0) {
            Integer code = VirtualKeyDefinition.getCodeByName(name);
            if (code != null) {
                cir.setReturnValue(code);
            }
        }
    }
}
