package dev.virtualkeys;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VirtualKeysMod implements ModInitializer {
    public static final String MOD_ID = "virtualkeys";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        VirtualKeysConfig.load();
        LOGGER.info("[VirtualKeys] Initialized.");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
