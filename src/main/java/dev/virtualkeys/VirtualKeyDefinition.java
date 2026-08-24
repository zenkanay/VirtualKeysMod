package dev.virtualkeys;

import java.util.ArrayList;
import java.util.List;

/**
 * 仮想キーの定義。
 * Configファイルから読み込まれたラベルに応じて自動的にコードを割り当てる。
 */
public class VirtualKeyDefinition {

    public final String label;
    public final int glfwKey;

    public VirtualKeyDefinition(String label, int glfwKey) {
        this.label = label;
        this.glfwKey = glfwKey;
    }

    public static final List<VirtualKeyDefinition> ALL = new ArrayList<>();

    public static VirtualKeyDefinition getByCode(int code) {
        for (VirtualKeyDefinition def : ALL) {
            if (def.glfwKey == code) {
                return def;
            }
        }
        return null;
    }

    public static void updateFromConfig(List<String> keyLabels) {
        ALL.clear();
        // 401番以降を仮想キーコードとして割り当て
        int code = 401;
        for (String label : keyLabels) {
            ALL.add(new VirtualKeyDefinition(label, code++));
        }
        tryRegisterToMalilib();
        try {
            dev.virtualkeys.client.VirtualKeysClient.injectLanguageTranslations();
        } catch (Throwable ignored) {}
    }

    private static void tryRegisterToMalilib() {
        try {
            Class<?> keyCodesClass = Class.forName("fi.dy.masa.malilib.util.KeyCodes");
            java.lang.reflect.Field mapKeyToNameField = keyCodesClass.getDeclaredField("MAP_KEY_TO_NAME");
            java.lang.reflect.Field mapNameToKeyField = keyCodesClass.getDeclaredField("MAP_NAME_TO_KEY");
            mapKeyToNameField.setAccessible(true);
            mapNameToKeyField.setAccessible(true);
            
            java.util.Map mapKeyToName = (java.util.Map) mapKeyToNameField.get(null);
            java.util.Map mapNameToKey = (java.util.Map) mapNameToKeyField.get(null);
            
            if (mapKeyToName != null && mapNameToKey != null) {
                // Clear any previously registered virtual keys to prevent leftover bindings if keys are renamed/deleted
                for (int c = 401; c <= 1000; c++) {
                    Object name = mapKeyToName.remove(c);
                    if (name instanceof String) {
                        mapNameToKey.remove((String) name);
                    }
                }
                
                // Register current virtual keys
                for (VirtualKeyDefinition def : ALL) {
                    mapKeyToName.put(def.glfwKey, def.label);
                    mapNameToKey.put(def.label, def.glfwKey);
                }
                System.out.println("[VirtualKeys] Registered " + ALL.size() + " keys to Malilib's KeyCodes map.");
            }
        } catch (Throwable ignored) {
        }
    }
}
