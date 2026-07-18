package dev.virtualkeys;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.minecraftforge.fml.loading.FMLPaths;
import java.io.File;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class VirtualKeysConfig {

    public enum HorizontalAlign {
        LEFT, CENTER, RIGHT
    }

    public enum VerticalAlign {
        TOP, CENTER, BOTTOM
    }

    @SerializedName("keys")
    public List<String> keys = new ArrayList<>();

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

    // Current active config
    public static VirtualKeysConfig INSTANCE = new VirtualKeysConfig();

    public static void load() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve("virtualkeys.json");
        File configFile = configPath.toFile();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        if (!configFile.exists()) {
            INSTANCE = new VirtualKeysConfig();
            for (int i = 1; i <= 12; i++) {
                INSTANCE.keys.add("VKey" + i);
            }
            save();
        } else {
            try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                VirtualKeysConfig loaded = gson.fromJson(reader, VirtualKeysConfig.class);
                if (loaded != null) {
                    INSTANCE = loaded;
                    if (INSTANCE.keys == null) {
                        INSTANCE.keys = new ArrayList<>();
                    }
                    if (INSTANCE.keys.isEmpty()) {
                        for (int i = 1; i <= 32; i++) {
                            INSTANCE.keys.add("VKey" + i);
                        }
                    }
                    if (INSTANCE.buttonWidth < 1) INSTANCE.buttonWidth = 48;
                    if (INSTANCE.buttonHeight < 1) INSTANCE.buttonHeight = 18;
                    if (INSTANCE.buttonGap < 0) INSTANCE.buttonGap = 3;
                    if (INSTANCE.keysPerRow < 1) INSTANCE.keysPerRow = 3;
                    if (INSTANCE.horizontalAlign == null) INSTANCE.horizontalAlign = HorizontalAlign.CENTER;
                    if (INSTANCE.verticalAlign == null) INSTANCE.verticalAlign = VerticalAlign.CENTER;
                }
            } catch (Exception e) {
                VirtualKeysMod.LOGGER.error("Failed to load config, using defaults", e);
                INSTANCE = new VirtualKeysConfig();
                for (int i = 1; i <= 32; i++) {
                    INSTANCE.keys.add("VKey" + i);
                }
            }
        }

        // Update virtual key definitions
        VirtualKeyDefinition.updateFromConfig(INSTANCE.keys);
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
