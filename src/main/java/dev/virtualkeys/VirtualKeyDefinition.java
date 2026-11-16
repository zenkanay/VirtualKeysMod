package dev.virtualkeys;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 仮想キーの定義。
 * Configファイルから読み込まれた全タブのキーに応じてコードを割り当て・同期する。
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
    }

    /**
     * 全タブのキーを走査し、401番以降の重複しない仮想キーコードを割り当てて ALL と maLiLib マップを更新する。
     */
    public static void updateFromTabs(List<VirtualKeysConfig.TabConfig> tabs) {
        ALL.clear();
        Set<Integer> usedCodes = new HashSet<>();

        // 1. 既に割り当て済みの有効なコードを予約（重複は後続で再採番）
        for (VirtualKeysConfig.TabConfig tab : tabs) {
            if (tab.keys == null) tab.keys = new ArrayList<>();
            if (tab.keyCodes == null) tab.keyCodes = new ArrayList<>();
            while (tab.keyCodes.size() > tab.keys.size()) {
                tab.keyCodes.remove(tab.keyCodes.size() - 1);
            }
            for (int i = 0; i < tab.keys.size(); i++) {
                if (i < tab.keyCodes.size()) {
                    Integer existing = tab.keyCodes.get(i);
                    if (existing != null && existing >= 401 && usedCodes.add(existing)) {
                        continue;
                    }
                    tab.keyCodes.set(i, null);
                } else {
                    tab.keyCodes.add(null);
                }
            }
        }

        // 2. 未割り当てのキーへ空きコードを採番して ALL に登録
        int nextCode = 401;
        for (VirtualKeysConfig.TabConfig tab : tabs) {
            for (int i = 0; i < tab.keys.size(); i++) {
                Integer code = tab.keyCodes.get(i);
                if (code == null) {
                    while (usedCodes.contains(nextCode)) {
                        nextCode++;
                    }
                    code = nextCode++;
                    usedCodes.add(code);
                    tab.keyCodes.set(i, code);
                }
                ALL.add(new VirtualKeyDefinition(tab.keys.get(i), code));
            }
        }

        tryRegisterToMalilib();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
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
                for (int c = 401; c <= 2000; c++) {
                    Object name = mapKeyToName.remove(c);
                    if (name instanceof String) {
                        mapNameToKey.remove(name);
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
