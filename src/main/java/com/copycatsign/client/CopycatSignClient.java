package com.copycatsign.client;

import com.copycatsign.CopycatSign;
import com.copycatsign.block.AbstractPictureBlock;
import com.copycatsign.block.PictureBlockEntities;
import com.copycatsign.block.PictureBlocks;
import com.copycatsign.client.gui.PictureEditorScreen;
import com.copycatsign.network.payload.ImageDataResponsePayload;
import com.copycatsign.network.payload.ImageUploadResultPayload;
import com.copycatsign.network.payload.OpenPictureEditorPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

import java.util.List;
import java.util.Map;

/**
 * Client-only hookups: wraps every baked model variant of our picture blocks in a PictureBakedModel
 * so a player-chosen back/edge material (see PictureBlockEntity/PictureMaterialProperty) can
 * re-texture the sign at render time, and drains ClientPictureBridge every client tick to do the
 * actual Screen-opening/texture work. Kept as its own Dist.CLIENT-gated class (rather than wiring
 * these listeners straight from CopycatSign's constructor) because everything here (BakedModel,
 * PictureEditorScreen, ClientImageManager, ...) is client-only - FML skips loading this class
 * entirely on a dedicated server thanks to @EventBusSubscriber's Dist filter.
 * <p>
 * Nutzer-Fund (Live-Test auf echtem Dedicated Server): die 3 Client-Payloads wurden vorher HIER per
 * eigenem event.registrar("1") registriert - lief in Singleplayer unbemerkt (physische Seite ist
 * dort immer CLIENT), verpuffte auf einem echten Dedicated Server aber wirkungslos, weil diese Klasse
 * dort nie geladen wird und der Server den Kanal für diese 3 Typen deshalb überhaupt nicht kennt. Die
 * Registrierung wandert deshalb nach CopycatSignNetwork (common) - siehe ClientPictureBridge
 * Klassenkommentar für die Details des Fixes.
 */
@EventBusSubscriber(modid = CopycatSign.MOD_ID, value = Dist.CLIENT)
public final class CopycatSignClient {

    private CopycatSignClient() {
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        wrapModelsFor(PictureBlocks.PICTURE_BLANK.get(), models);
        wrapModelsFor(PictureBlocks.HOGWARTS_EXPRESS_SCHILD.get(), models);
        wrapModelsFor(PictureBlocks.PICTURE_5972.get(), models);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(PictureBlockEntities.PICTURE.get(), PictureBlockEntityRenderer::new);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        OpenPictureEditorPayload open = ClientPictureBridge.takePendingOpen();
        if (open != null) {
            Minecraft.getInstance().setScreen(new PictureEditorScreen(
                open.pos(), open.imageHash(), open.panX(), open.panY(), open.zoom(), open.thickness()));
        }

        ImageUploadResultPayload uploadResult = ClientPictureBridge.takePendingUploadResult();
        if (uploadResult != null) {
            if (Minecraft.getInstance().screen instanceof PictureEditorScreen screen) {
                screen.onUploadResult(uploadResult);
            } else if (!uploadResult.success()) {
                CopycatSign.LOGGER.warn("image upload rejected: {}", uploadResult.hashOrErrorKey());
            }
        }

        ImageDataResponsePayload imageData;
        while ((imageData = ClientPictureBridge.pollImageData()) != null) {
            ClientImageManager.handleResponse(imageData);
        }
    }

    private static void wrapModelsFor(AbstractPictureBlock block, Map<ModelResourceLocation, BakedModel> models) {
        List<BlockState> states = block.getStateDefinition().getPossibleStates();
        for (BlockState state : states) {
            ModelResourceLocation key = BlockModelShaper.stateToModelLocation(state);
            BakedModel original = models.get(key);
            if (original != null && !(original instanceof PictureBakedModel)) {
                models.put(key, new PictureBakedModel(original));
            }
        }
    }
}
