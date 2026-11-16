package dev.virtualkeys.client.mixin;

import dev.virtualkeys.VirtualKeyDefinition;
import dev.virtualkeys.client.MalilibCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "fi.dy.masa.malilib.event.InputEventHandler", remap = false)
public class MalilibInputEventHandlerMixin {

    @Inject(method = "onKeyInput", at = @At("HEAD"))
    private void onMalilibKeyInput(KeyEvent input, int action, Minecraft mc, CallbackInfoReturnable<Boolean> cir) {
        if (input != null && VirtualKeyDefinition.getByCode(input.key()) != null) {
            MalilibCompat.invokeOnKeyInputPre(input, action);
        }
    }
}
