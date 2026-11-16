package dev.virtualkeys;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VirtualKeysConfig {

    public enum HorizontalAlign {
        LEFT, CENTER, RIGHT
    }

    public enum VerticalAlign {
        TOP, CENTER, BOTTOM
    }

    public static class TabConfig {
        @SerializedName("name")
        public String name = "Tab 1";

        @SerializedName("keys")
        public List<String> keys = new ArrayList<>();

        @SerializedName("key_codes")
        public List<Integer> keyCodes = new ArrayList<>();

        @SerializedName("button_width")
        public int buttonWidth = 48;

        @SerializedName("button_height")
        public int buttonHeight = 18;

        @SerializedName("button_gap")
        public int buttonGap = 3;

        @SerializedName("horizontal_align")
        public HorizontalAlign horizontalAlign = HorizontalAlign.CENTER;

        @SerializedName("vertical_align")
        public VerticalAlign verticalAlign = VerticalAlign.CENTER;

        @SerializedName("keys_per_row")
        public int keysPerRow = 3;

        public void sanitize(int index) {
            if (name == null || name.trim().isEmpty()) {
                name = "Tab " + (index + 1);
            }
            if (keys == null) keys = new ArrayList<>();
            if (keyCodes == null) keyCodes = new ArrayList<>();
            if (buttonWidth < 1) buttonWidth = 48;
            if (buttonHeight < 1) buttonHeight = 18;
            if (buttonGap < 0) buttonGap = 3;
            if (keysPerRow < 1) keysPerRow = 3;
            if (horizontalAlign == null) horizontalAlign = HorizontalAlign.CENTER;
            if (verticalAlign == null) verticalAlign = VerticalAlign.CENTER;
        }
    }

    @SerializedName("tabs")
    public List<TabConfig> tabs = new ArrayList<>();

    @SerializedName("active_tab")
    public int activeTab = 0;

    @SerializedName("keys")
    private List<String> legacyKeys;

    @SerializedName("button_width")
    private Integer legacyButtonWidth;

    @SerializedName("button_height")
    private Integer legacyButtonHeight;

    @SerializedName("button_gap")
    private Integer legacyButtonGap;

    @SerializedName("horizontal_align")
    private HorizontalAlign legacyHorizontalAlign;

    @SerializedName("vertical_align")
    private VerticalAlign legacyVerticalAlign;

    @SerializedName("keys_per_row")
    private Integer legacyKeysPerRow;

    // Current active config
    public static VirtualKeysConfig INSTANCE = new VirtualKeysConfig();

    public TabConfig getActiveTab() {
        if (tabs == null || tabs.isEmpty()) {
            TabConfig def = createDefaultTab("Tab 1", 12);
            tabs = new ArrayList<>();
            tabs.add(def);
            activeTab = 0;
        }
        if (activeTab < 0 || activeTab >= tabs.size()) {
            activeTab = Math.max(0, Math.min(activeTab, tabs.size() - 1));
        }
        return tabs.get(activeTab);
    }

    public int allocateNextKeyCode() {
        Set<Integer> used = new HashSet<>();
        if (tabs != null) {
            for (TabConfig tab : tabs) {
                if (tab.keyCodes != null) {
                    used.addAll(tab.keyCodes);
                }
            }
        }
        int candidate = 401;
        while (used.contains(candidate)) {
            candidate++;
        }
        return candidate;
    }

    public String allocateNextDefaultKeyLabel() {
        Set<String> used = new HashSet<>();
        if (tabs != null) {
            for (TabConfig tab : tabs) {
                if (tab.keys != null) {
                    used.addAll(tab.keys);
                }
            }
        }
        int num = 1;
        while (used.contains("VKey" + num)) {
            num++;
        }
        return "VKey" + num;
    }

    private static TabConfig createDefaultTab(String name, int keyCount) {
        TabConfig tab = new TabConfig();
        tab.name = name;
        for (int i = 1; i <= keyCount; i++) {
            tab.keys.add("VKey" + i);
            tab.keyCodes.add(400 + i);
        }
        return tab;
    }

    private void clearLegacyFields() {
        legacyKeys = null;
        legacyButtonWidth = null;
        legacyButtonHeight = null;
        legacyButtonGap = null;
        legacyHorizontalAlign = null;
        legacyVerticalAlign = null;
        legacyKeysPerRow = null;
    }

    public static void load() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve("virtualkeys.json");
        File configFile = configPath.toFile();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        boolean shouldSave = false;

        if (!configFile.exists()) {
            INSTANCE = new VirtualKeysConfig();
            INSTANCE.tabs.add(createDefaultTab("Tab 1", 12));
            INSTANCE.activeTab = 0;
            shouldSave = true;
        } else {
            try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                VirtualKeysConfig loaded = gson.fromJson(reader, VirtualKeysConfig.class);
                if (loaded != null) {
                    INSTANCE = loaded;
                    if (INSTANCE.tabs == null) {
                        INSTANCE.tabs = new ArrayList<>();
                    }
                    if (INSTANCE.tabs.isEmpty()) {
                        TabConfig migrated = new TabConfig();
                        migrated.name = "Tab 1";
                        if (INSTANCE.legacyKeys != null && !INSTANCE.legacyKeys.isEmpty()) {
                            migrated.keys.addAll(INSTANCE.legacyKeys);
                        } else {
                            for (int i = 1; i <= 12; i++) {
                                migrated.keys.add("VKey" + i);
                            }
                        }
                        if (INSTANCE.legacyButtonWidth != null) migrated.buttonWidth = INSTANCE.legacyButtonWidth;
                        if (INSTANCE.legacyButtonHeight != null) migrated.buttonHeight = INSTANCE.legacyButtonHeight;
                        if (INSTANCE.legacyButtonGap != null) migrated.buttonGap = INSTANCE.legacyButtonGap;
                        if (INSTANCE.legacyHorizontalAlign != null) migrated.horizontalAlign = INSTANCE.legacyHorizontalAlign;
                        if (INSTANCE.legacyVerticalAlign != null) migrated.verticalAlign = INSTANCE.legacyVerticalAlign;
                        if (INSTANCE.legacyKeysPerRow != null) migrated.keysPerRow = INSTANCE.legacyKeysPerRow;
                        INSTANCE.tabs.add(migrated);
                        INSTANCE.clearLegacyFields();
                        shouldSave = true;
                    }
                } else {
                    INSTANCE = new VirtualKeysConfig();
                    INSTANCE.tabs.add(createDefaultTab("Tab 1", 12));
                    shouldSave = true;
                }
            } catch (Exception e) {
                VirtualKeysMod.LOGGER.error("Failed to load config, using defaults", e);
                INSTANCE = new VirtualKeysConfig();
                INSTANCE.tabs.add(createDefaultTab("Tab 1", 12));
            }
        }

        if (INSTANCE.tabs.isEmpty()) {
            INSTANCE.tabs.add(createDefaultTab("Tab 1", 12));
        }
        for (int i = 0; i < INSTANCE.tabs.size(); i++) {
            INSTANCE.tabs.get(i).sanitize(i);
        }
        if (INSTANCE.activeTab < 0 || INSTANCE.activeTab >= INSTANCE.tabs.size()) {
            INSTANCE.activeTab = 0;
        }

        // Update virtual key definitions
        VirtualKeyDefinition.updateFromTabs(INSTANCE.tabs);

        if (shouldSave) {
            save();
        }
    }

    public static void save() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve("virtualkeys.json");
        File configFile = configPath.toFile();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        try {
            configFile.getParentFile().mkdirs();
            try (BufferedWriter writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                gson.toJson(INSTANCE, writer);
            }
        } catch (Exception e) {
            VirtualKeysMod.LOGGER.error("Failed to save config", e);
        }
    }
}
