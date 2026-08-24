package dev.virtualkeys;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(VirtualKeysMod.MOD_ID)
public class VirtualKeysMod {
    public static final String MOD_ID = "virtualkeys";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public VirtualKeysMod() {
        VirtualKeysConfig.load();
        LOGGER.info("[VirtualKeys] Initialized config.");

        // Register client setup listener
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
        
        // Register key mapping registration
        FMLJavaModLoadingContext.get().getModEventBus().addListener(dev.virtualkeys.client.VirtualKeysClient::registerKeyMappings);
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            dev.virtualkeys.client.VirtualKeysClient.init();
        });
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
