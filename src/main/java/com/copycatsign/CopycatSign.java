package com.copycatsign;

import com.copycatsign.block.PictureBlockEntities;
import com.copycatsign.block.PictureBlocks;
import com.copycatsign.network.CopycatSignNetwork;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(CopycatSign.MOD_ID)
public class CopycatSign {

    public static final String MOD_ID = "copycatsign";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CopycatSign(IEventBus modEventBus, ModContainer modContainer) {
        PictureBlocks.register(modEventBus);
        PictureBlockEntities.register(modEventBus);
        CopycatSignNetwork.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, CopycatSignConfig.SPEC);
        modEventBus.addListener(this::commonSetup);
        LOGGER.info("Copycat Sign loading...");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Copycat Sign common setup complete.");
    }
}
