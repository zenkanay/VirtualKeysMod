package dev.virtualkeys.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = dev.virtualkeys.VirtualKeysMod.MOD_ID, value = Dist.CLIENT)
public class VirtualKeysClient {

    public static final String CATEGORY = "category.virtualkeys";
    public static KeyMapping openGuiKey;
    public static KeyMapping alwaysOpenGuiKey;

    public static boolean overlayActive = false;
    public static VirtualKeyScreen overlayScreen = null;
    public static boolean overlayCapturedPress = false;

    private static final java.util.Set<Integer> pressedVirtualKeys = new java.util.HashSet<>();

    public static void init() {
        injectLanguageTranslations();
    }

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
     * Determine if configuring/binding keys in options or other config screens.
     */
    public static boolean isConfiguringControls() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) return false;

        // Only block in the actual KeyBinds screen when a key is actively being bound
        if (mc.screen instanceof net.minecraft.client.gui.screens.controls.KeyBindsScreen) {
            try {
                // Mojang mapping: selectedKey
                java.lang.reflect.Field field = net.minecraft.client.gui.screens.controls.KeyBindsScreen.class.getDeclaredField("selectedKey");
                field.setAccessible(true);
                return field.get(mc.screen) != null;
            } catch (Exception e) {
                try {
                    // SRG mapping fallback: f_91823_
                    java.lang.reflect.Field fieldSrg = net.minecraft.client.gui.screens.controls.KeyBindsScreen.class.getDeclaredField("f_91823_");
                    fieldSrg.setAccessible(true);
                    return fieldSrg.get(mc.screen) != null;
                } catch (Exception e2) {
                    // Safe default: do not block if reflection fails
                    return false;
                }
            }
        }
        
        return false;
    }

    /**
     * Determine if typing in any standard text field to prevent triggers.
     */
    public static boolean isTypingInTextField() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) return false;

        if (mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen) return true;

        String cls = mc.screen.getClass().getName().toLowerCase();
        if (cls.contains("sign") && cls.contains("edit")) return true;
        if (cls.contains("book") && cls.contains("edit")) return true;
        if (cls.contains("anvil")) return true;
        if (cls.contains("commandblock")) return true;

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
        overlayScreen.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        overlayActive = true;
    }

    public static void closeOverlay() {
        overlayActive = false;
        overlayScreen = null;
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        openGuiKey = new KeyMapping(
            "key.virtualkeys.open_gui",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            CATEGORY
        );
        alwaysOpenGuiKey = new KeyMapping(
            "key.virtualkeys.always_open_gui",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            CATEGORY
        );
        event.register(openGuiKey);
        event.register(alwaysOpenGuiKey);
    }

    // --- Forge Native Events ---

    // 1. In-game key inputs (mc.screen == null)
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return; // Only process when no screen is open

        int key = event.getKey();
        int scancode = event.getScanCode();
        int action = event.getAction();

        handleToggleLogic(mc, key, scancode, action);
    }

    // 2. GUI key inputs (mc.screen != null)
    @SubscribeEvent
    public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) return;

        int key = event.getKeyCode();
        int scancode = event.getScanCode();
        // ScreenEvent.KeyPressed is only fired on GLFW_PRESS (or REPEAT)
        
        // Let the overlay handle typing/keys first
        if (overlayActive && overlayScreen != null && mc.screen != overlayScreen) {
            if (overlayScreen.keyPressed(key, scancode, event.getModifiers())) {
                event.setCanceled(true);
                return;
            }
        }

        if (handleToggleLogic(mc, key, scancode, GLFW.GLFW_PRESS)) {
            event.setCanceled(true);
        }
    }

    // Shared toggle logic
    private static boolean handleToggleLogic(Minecraft mc, int key, int scancode, int action) {
        boolean isEditing = (mc.screen instanceof VirtualKeyScreen && ((VirtualKeyScreen) mc.screen).isEditingText())
                || (overlayActive && overlayScreen != null && overlayScreen.isEditingText());

        if (isEditing) {
            return false;
        }

        boolean matchesOpen = false;
        if (openGuiKey != null) {
            matchesOpen = openGuiKey.matches(key, scancode)
                || (openGuiKey.getKey().getType() == InputConstants.Type.KEYSYM && openGuiKey.getKey().getValue() == key);
        }

        if (matchesOpen && action == GLFW.GLFW_PRESS) {
            if (isTypingInTextField() || isConfiguringControls()) {
                return false;
            }
            toggleGui(mc);
            return true;
        }

        boolean matchesAlwaysOpen = false;
        if (alwaysOpenGuiKey != null) {
            matchesAlwaysOpen = alwaysOpenGuiKey.matches(key, scancode)
                || (alwaysOpenGuiKey.getKey().getType() == InputConstants.Type.KEYSYM && alwaysOpenGuiKey.getKey().getValue() == key);
        }

        if (matchesAlwaysOpen && action == GLFW.GLFW_PRESS) {
            if (isConfiguringControls()) {
                return false;
            }
            toggleGui(mc);
            return true;
        }

        return false;
    }

    private static void toggleGui(Minecraft mc) {
        if (mc.screen instanceof VirtualKeyScreen) {
            ((VirtualKeyScreen) mc.screen).closePanel();
        } else if (overlayActive) {
            closeOverlay();
        } else {
            if (mc.screen != null) {
                openOverlay(mc.screen);
            } else {
                mc.setScreen(new VirtualKeyScreen(null));
            }
        }
    }

    // 3. Movement overrides while the overlay/screen is open
    @SubscribeEvent
    public static void onInputUpdate(MovementInputUpdateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof VirtualKeyScreen || overlayActive) {
            long window = mc.getWindow().getWindow();
            
            // Read raw key states from GLFW
            boolean up = InputConstants.isKeyDown(window, mc.options.keyUp.getKey().getValue());
            boolean down = InputConstants.isKeyDown(window, mc.options.keyDown.getKey().getValue());
            boolean left = InputConstants.isKeyDown(window, mc.options.keyLeft.getKey().getValue());
            boolean right = InputConstants.isKeyDown(window, mc.options.keyRight.getKey().getValue());
            boolean jump = InputConstants.isKeyDown(window, mc.options.keyJump.getKey().getValue());
            boolean shift = InputConstants.isKeyDown(window, mc.options.keyShift.getKey().getValue());
            boolean sprint = InputConstants.isKeyDown(window, mc.options.keySprint.getKey().getValue());

            event.getInput().up = up;
            event.getInput().down = down;
            event.getInput().left = left;
            event.getInput().right = right;
            event.getInput().jumping = jump;
            event.getInput().shiftKeyDown = shift;

            // Recalculate impulses
            event.getInput().forwardImpulse = up == down ? 0.0F : (up ? 1.0F : -1.0F);
            event.getInput().leftImpulse = left == right ? 0.0F : (left ? 1.0F : -1.0F);

            if (shift) {
                event.getInput().forwardImpulse *= 0.3F;
                event.getInput().leftImpulse *= 0.3F;
            }

            mc.options.keySprint.setDown(sprint);
            if (sprint && mc.player != null && event.getInput().forwardImpulse > 0 && !shift && !mc.player.isSprinting()) {
                mc.player.setSprinting(true);
            }
        }
    }

    // 4. Mouse click handling (only for overlay mode where mc.screen is the parent)
    @SubscribeEvent
    public static void onScreenMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (overlayActive && overlayScreen != null && event.getScreen() != overlayScreen) {
            Minecraft mc = Minecraft.getInstance();
            double mx = event.getMouseX();
            double my = event.getMouseY();
            int button = event.getButton();

            if (overlayScreen.isMouseOverPanel(mx, my)) {
                overlayScreen.handleMouseClick(mx, my, button, GLFW.GLFW_PRESS);
                overlayCapturedPress = true;
                event.setCanceled(true); // Prevent click from reaching underlying buttons
            }
        } else {
            overlayCapturedPress = false;
        }
    }

    @SubscribeEvent
    public static void onScreenMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (overlayActive && overlayScreen != null && event.getScreen() != overlayScreen) {
            double mx = event.getMouseX();
            double my = event.getMouseY();
            int button = event.getButton();

            if (overlayCapturedPress) {
                overlayScreen.handleMouseRelease(mx, my, button);
                overlayCapturedPress = false;
                event.setCanceled(true);
            } else if (overlayScreen.isMouseOverPanel(mx, my)) {
                overlayScreen.handleMouseRelease(mx, my, button);
                // Optionally cancel if needed, but release events are usually fine
            }
        }
    }

    // 5. Overlay Rendering (Frontmost Z-axis)
    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (overlayActive && overlayScreen != null && event.getScreen() != overlayScreen) {
            Minecraft mc = Minecraft.getInstance();
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();
            if (overlayScreen.width != w || overlayScreen.height != h) {
                overlayScreen.init(mc, w, h);
            }
            
            event.getGuiGraphics().pose().pushPose();
            // Push overlay far forward to not be overlapped by pause menu widgets
            event.getGuiGraphics().pose().translate(0.0F, 0.0F, 500.0F);
            
            overlayScreen.render(event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), event.getPartialTick());
            
            event.getGuiGraphics().pose().popPose();
        }
    }

    // 6. Keep overlay open/closed synchronously with parent screen
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        injectLanguageTranslations(); // Keep translations injected when screens open

        if (overlayActive && overlayScreen != null) {
            net.minecraft.client.gui.screens.Screen screen = event.getScreen();
            if (screen == null || (screen != overlayScreen && screen != overlayScreen.getParentScreen())) {
                closeOverlay();
            }
        }
    }

    /**
     * Inject custom keybind labels into the vanilla Language storage wrapper.
     */
    public static void injectLanguageTranslations() {
        try {
            net.minecraft.locale.Language current = net.minecraft.locale.Language.getInstance();
            if (!(current instanceof VirtualKeysLanguage)) {
                net.minecraft.locale.Language.inject(new VirtualKeysLanguage(current));
            }
        } catch (Exception e) {
            // Silently fail
        }
    }

    /**
     * Custom Language Wrapper to translate "key.keyboard.401" to custom labels (e.g., "VKey1")
     */
    private static class VirtualKeysLanguage extends net.minecraft.locale.Language {
        private final net.minecraft.locale.Language original;

        public VirtualKeysLanguage(net.minecraft.locale.Language original) {
            this.original = original;
        }

        @Override
        public String getOrDefault(String key, String defaultValue) {
            if (key != null && key.startsWith("key.keyboard.")) {
                try {
                    int code = Integer.parseInt(key.substring("key.keyboard.".length()));
                    dev.virtualkeys.VirtualKeyDefinition def = dev.virtualkeys.VirtualKeyDefinition.getByCode(code);
                    if (def != null) {
                        return def.label;
                    }
                } catch (NumberFormatException ignored) {}
            }
            return this.original.getOrDefault(key, defaultValue);
        }

        @Override
        public boolean has(String key) {
            if (key != null && key.startsWith("key.keyboard.")) {
                try {
                    int code = Integer.parseInt(key.substring("key.keyboard.".length()));
                    if (dev.virtualkeys.VirtualKeyDefinition.getByCode(code) != null) {
                        return true;
                    }
                } catch (NumberFormatException ignored) {}
            }
            return this.original.has(key);
        }

        @Override
        public boolean isDefaultRightToLeft() {
            return this.original.isDefaultRightToLeft();
        }

        @Override
        public net.minecraft.util.FormattedCharSequence getVisualOrder(net.minecraft.network.chat.FormattedText text) {
            return this.original.getVisualOrder(text);
        }
    }
}
