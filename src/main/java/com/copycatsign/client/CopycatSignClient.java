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
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;
import java.util.Map;

/**
 * Client-only hookups: wraps every baked model variant of our picture blocks in a PictureBakedModel
 * so a player-chosen back/edge material (see PictureBlockEntity/PictureMaterialProperty) can
 * re-texture the sign at render time, and registers the client-bound network payloads. Kept as its
 * own Dist.CLIENT-gated class (rather than wiring these listeners straight from CopycatSign's
 * constructor) because everything here (BakedModel, ImageUploadResultPayload's eventual GUI
 * consumer, ...) is client-only - FML skips loading this class entirely on a dedicated server thanks
 * to @EventBusSubscriber's Dist filter. CopycatSignNetwork registers the server-bound payloads
 * separately in common code for exactly that reason - it must never be called from here or vice
 * versa, or a dedicated server would end up loading client-only classes.
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
    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(ImageUploadResultPayload.TYPE, ImageUploadResultPayload.STREAM_CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> {
                if (Minecraft.getInstance().screen instanceof PictureEditorScreen screen) {
                    screen.onUploadResult(payload);
                } else if (!payload.success()) {
                    CopycatSign.LOGGER.warn("image upload rejected: {}", payload.hashOrErrorKey());
                }
            }));
        registrar.playToClient(ImageDataResponsePayload.TYPE, ImageDataResponsePayload.STREAM_CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> ClientImageManager.handleResponse(payload)));
        registrar.playToClient(OpenPictureEditorPayload.TYPE, OpenPictureEditorPayload.STREAM_CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> Minecraft.getInstance().setScreen(
                new PictureEditorScreen(payload.pos(), payload.imageHash(), payload.panX(), payload.panY(), payload.zoom(), payload.thickness()))));
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
