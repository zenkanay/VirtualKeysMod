package dev.virtualkeys.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class VirtualKeysClient implements ClientModInitializer {

    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(dev.virtualkeys.VirtualKeysMod.id("category"));
    public static KeyMapping openGuiKey;
    public static KeyMapping alwaysOpenGuiKey;

    public static boolean overlayActive = false;
    public static VirtualKeyScreen overlayScreen = null;
    public static boolean overlayCapturedPress = false;

    private static final java.util.Set<Integer> pressedVirtualKeys = new java.util.HashSet<>();

    public static void setVirtualKeyDown(int glfwKey, boolean down) {
        if (down) {
            pressedVirtualKeys.add(glfwKey);
        } else {
            pressedVirtualKeys.remove(glfwKey);
        }
    }

    public static boolean isVirtualKeyDown(int glfwKey) {
        return pressedVirtualKeys.contains(glfwKey);
    }

    /**
     * チャット・アンビル・EditBox等、文字入力中かどうかを判定する。
     * バニラ画面のみ対象（Malilib/Tweakeroo は対象外）。
     */
    public static boolean isTypingInTextField() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) return false;

        // Chat screen
        if (mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen) return true;

        // 看板・本・アンビル・コマンドブロックなどの文字入力 GUI（クラス名で判定）
        String cls = mc.screen.getClass().getName().toLowerCase();
        if (cls.contains("sign") && cls.contains("edit")) return true;
        if (cls.contains("book") && cls.contains("edit")) return true;
        if (cls.contains("anvil")) return true;
        if (cls.contains("commandblock")) return true;

        // フォーカスされたウィジェットがテキスト入力系であるか判定（他Mod対応含む）
        net.minecraft.client.gui.components.events.GuiEventListener focused = mc.screen.getFocused();
        if (focused != null) {
            if (focused instanceof net.minecraft.client.gui.components.EditBox) {
                return ((net.minecraft.client.gui.components.EditBox) focused).isVisible();
            }
            
            String fullName = focused.getClass().getName().toLowerCase();
            String simpleName = focused.getClass().getSimpleName().toLowerCase();
            
            if (simpleName.contains("editbox") || 
                simpleName.contains("textfield") || 
                simpleName.contains("textbox") || 
                simpleName.contains("inputfield") || 
                simpleName.contains("search") ||
                fullName.contains("widgettextfield")) {
                return true;
            }
        }
        return false;
    }

    public static void openOverlay(net.minecraft.client.gui.screens.Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        overlayScreen = new VirtualKeyScreen(parent);
        overlayScreen.init(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        overlayActive = true;
    }

    public static void closeOverlay() {
        overlayActive = false;
        overlayScreen = null;
    }

    @Override
    public void onInitializeClient() {
        // ホットキー登録 (デフォルト: F8)
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.virtualkeys.open_gui",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            CATEGORY
        ));

        // 常に開くホットキー (デフォルト: 未割当)
        alwaysOpenGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.virtualkeys.always_open_gui",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            CATEGORY
        ));
    }
}
