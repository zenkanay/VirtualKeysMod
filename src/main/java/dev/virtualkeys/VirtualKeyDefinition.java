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

    public String label;
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

    /**
     * 仮想キーの表示名（大文字小文字・前後空白無視）からキーコードを逆引きする。
     */
    public static Integer getCodeByName(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        for (VirtualKeyDefinition def : ALL) {
            if (def.label != null && def.label.equalsIgnoreCase(trimmed)) {
                return def.glfwKey;
            }
        }
        return null;
    }

    public static void updateFromConfig(List<String> keyLabels) {
        ALL.clear();
        int code = 401;
        for (String label : keyLabels) {
            ALL.add(new VirtualKeyDefinition(label, code++));
        }
        tryRegisterToMalilib();
    }

    public static void updateFromTabs(List<VirtualKeysConfig.TabConfig> tabs) {
        ALL.clear();
        Set<Integer> usedCodes = new HashSet<>();

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

    private static final java.util.Map<String, Set<Integer>> REGISTERED_MALILIB_CODES = new java.util.HashMap<>();
    private static final java.util.Map<String, Set<String>> REGISTERED_MALILIB_NAMES = new java.util.HashMap<>();

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void tryRegisterToMalilib() {
        String className = "fi.dy.masa.malilib.util.KeyCodes";
        try {
            Class<?> keyCodesClass = Class.forName(className);
            java.lang.reflect.Field mapKeyToNameField = keyCodesClass.getDeclaredField("MAP_KEY_TO_NAME");
            java.lang.reflect.Field mapNameToKeyField = keyCodesClass.getDeclaredField("MAP_NAME_TO_KEY");
            mapKeyToNameField.setAccessible(true);
            mapNameToKeyField.setAccessible(true);

            java.util.Map mapKeyToName = (java.util.Map) mapKeyToNameField.get(null);
            java.util.Map mapNameToKey = (java.util.Map) mapNameToKeyField.get(null);

            if (mapKeyToName != null && mapNameToKey != null) {
                Set<Integer> prevCodes = REGISTERED_MALILIB_CODES.computeIfAbsent(className, k -> new HashSet<>());
                Set<String> prevNames = REGISTERED_MALILIB_NAMES.computeIfAbsent(className, k -> new HashSet<>());

                for (Integer prevCode : prevCodes) {
                    mapKeyToName.remove(prevCode);
                }
                for (String prevName : prevNames) {
                    mapNameToKey.remove(prevName);
                }
                prevCodes.clear();
                prevNames.clear();

                for (VirtualKeyDefinition def : ALL) {
                    if (!mapKeyToName.containsKey(def.glfwKey)) {
                        mapKeyToName.put(def.glfwKey, def.label);
                        prevCodes.add(def.glfwKey);
                    }
                    if (def.label != null && !def.label.isEmpty() && !mapNameToKey.containsKey(def.label)) {
                        mapNameToKey.put(def.label, def.glfwKey);
                        prevNames.add(def.label);
                    }
                }
                System.out.println("[VirtualKeys] Registered " + ALL.size() + " keys to Malilib's KeyCodes map.");
            }
        } catch (Throwable ignored) {
        }
    }
}
