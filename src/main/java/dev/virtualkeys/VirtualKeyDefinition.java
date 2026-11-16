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

    private static final java.util.Map<String, Set<Integer>> REGISTERED_MALILIB_CODES = new java.util.HashMap<>();
    private static final java.util.Map<String, Set<String>> REGISTERED_MALILIB_NAMES = new java.util.HashMap<>();

    private static void tryRegisterToMalilib() {
        registerToMalilibMap("fi.dy.masa.malilib.util.input.ScanCodes", "MAP_SCAN_CODE_TO_NAME", "MAP_NAME_TO_SCAN_CODE");
        registerToMalilibMap("fi.dy.masa.malilib.util.input.KeyCodes", "MAP_KEY_TO_NAME", "MAP_NAME_TO_KEY");
        registerToMalilibMap("fi.dy.masa.malilib.util.KeyCodes", "MAP_KEY_TO_NAME", "MAP_NAME_TO_KEY");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerToMalilibMap(String className, String codeToNameFieldName, String nameToCodeFieldName) {
        try {
            Class<?> targetClass = Class.forName(className);
            java.lang.reflect.Field mapCodeToNameField = targetClass.getDeclaredField(codeToNameFieldName);
            java.lang.reflect.Field mapNameToCodeField = targetClass.getDeclaredField(nameToCodeFieldName);
            mapCodeToNameField.setAccessible(true);
            mapNameToCodeField.setAccessible(true);

            java.util.Map mapCodeToName = (java.util.Map) mapCodeToNameField.get(null);
            java.util.Map mapNameToCode = (java.util.Map) mapNameToCodeField.get(null);

            if (mapCodeToName != null && mapNameToCode != null) {
                Set<Integer> prevCodes = REGISTERED_MALILIB_CODES.computeIfAbsent(className, k -> new HashSet<>());
                Set<String> prevNames = REGISTERED_MALILIB_NAMES.computeIfAbsent(className, k -> new HashSet<>());

                for (Integer prevCode : prevCodes) {
                    mapCodeToName.remove(prevCode);
                }
                for (String prevName : prevNames) {
                    mapNameToCode.remove(prevName);
                }
                prevCodes.clear();
                prevNames.clear();

                for (VirtualKeyDefinition def : ALL) {
                    if (!mapCodeToName.containsKey(def.glfwKey)) {
                        mapCodeToName.put(def.glfwKey, def.label);
                        prevCodes.add(def.glfwKey);
                    }
                    if (def.label != null && !def.label.isEmpty() && !mapNameToCode.containsKey(def.label)) {
                        mapNameToCode.put(def.label, def.glfwKey);
                        prevNames.add(def.label);
                    }
                }
                System.out.println("[VirtualKeys] Registered " + ALL.size() + " keys to " + targetClass.getSimpleName() + ".");
            }
        } catch (Throwable ignored) {
        }
    }
}
