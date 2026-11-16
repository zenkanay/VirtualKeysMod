package dev.virtualkeys.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;

/**
 * Reflection bridge for optional maLiLib hotkey state cleanup without compile-time dependency.
 */
@Environment(EnvType.CLIENT)
public final class MalilibCompat {

    private static boolean initialized = false;
    private static Field pressedKeysField = null;

    private MalilibCompat() {
    }

    private static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            Class<?> keybindMultiClass = Class.forName("fi.dy.masa.malilib.hotkeys.KeybindMulti");
            pressedKeysField = keybindMultiClass.getDeclaredField("PRESSED_KEYS");
            pressedKeysField.setAccessible(true);
        } catch (Throwable ignored) {
            pressedKeysField = null;
        }
    }

    /**
     * Removes transiently held mouse button codes (-100..-90) from maLiLib's PRESSED_KEYS
     * so clicking a virtual key button can trigger single-key maLiLib hotkeys (allowExtraKeys=false).
     */
    public static void clearMouseButtonsFromPressedKeys() {
        init();
        if (pressedKeysField == null) {
            return;
        }
        try {
            Object value = pressedKeysField.get(null);
            if (value instanceof List<?> list) {
                Iterator<?> it = list.iterator();
                while (it.hasNext()) {
                    Object elem = it.next();
                    if (elem instanceof Integer code && code >= -100 && code <= -90) {
                        it.remove();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
