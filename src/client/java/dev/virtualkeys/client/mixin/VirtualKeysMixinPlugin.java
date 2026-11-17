package dev.virtualkeys.client.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * maLiLib が導入されている環境でのみ KeyCodesMixin を有効化する Mixin 設定プラグイン。
 */
public class VirtualKeysMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    private static final java.util.Map<String, String[]> MIXIN_REQUIRED_CLASSES = java.util.Map.of(
            ".KeyCodesMixin", new String[]{"fi/dy/masa/malilib/util/KeyCodes.class"},
            ".ScanCodesMixin", new String[]{"fi/dy/masa/malilib/util/input/ScanCodes.class"},
            ".MalilibInputKeyCodesMixin", new String[]{"fi/dy/masa/malilib/util/input/KeyCodes.class"},
            ".MalilibInputUtilsMixin", new String[]{"fi/dy/masa/malilib/util/input/InputUtils.class"},
            ".MalilibInputEventHandlerMixin", new String[]{
                    "fi/dy/masa/malilib/event/InputEventHandler.class",
                    "fi/dy/masa/malilib/util/input/ScanCodes.class"
            },
            ".MalilibGuiUtilsMixin", new String[]{"fi/dy/masa/malilib/util/GuiUtils.class"}
    );

    private static boolean hasClassResource(String resourcePath) {
        ClassLoader cl = VirtualKeysMixinPlugin.class.getClassLoader();
        if (cl != null && cl.getResource(resourcePath) != null) {
            return true;
        }
        ClassLoader ctx = Thread.currentThread().getContextClassLoader();
        return ctx != null && ctx.getResource(resourcePath) != null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        for (java.util.Map.Entry<String, String[]> entry : MIXIN_REQUIRED_CLASSES.entrySet()) {
            if (mixinClassName.endsWith(entry.getKey())) {
                for (String requiredClass : entry.getValue()) {
                    if (!hasClassResource(requiredClass)) {
                        return false;
                    }
                }
                return true;
            }
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
