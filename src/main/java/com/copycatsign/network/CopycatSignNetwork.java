package com.copycatsign.network;

import com.copycatsign.client.ClientPictureBridge;
import com.copycatsign.network.payload.ImageDataResponsePayload;
import com.copycatsign.network.payload.ImageRequestPayload;
import com.copycatsign.network.payload.ImageUploadPayload;
import com.copycatsign.network.payload.ImageUploadResultPayload;
import com.copycatsign.network.payload.OpenPictureEditorPayload;
import com.copycatsign.network.payload.PictureEditPayload;
import com.copycatsign.network.payload.PictureThicknessPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers ALL network payloads, both server-bound and client-bound, from common code (loads on
 * both dedicated server and client) - see ClientPictureBridge class comment for why this changed
 * from the original "client-bound payloads registered only in the Dist.CLIENT-gated
 * CopycatSignClient" design (worked in singleplayer, silently did nothing on a real dedicated
 * server). The client-bound handlers below only ever touch ClientPictureBridge (a plain data holder,
 * no net.minecraft.client.* import) - never Minecraft/Screen/texture classes directly, so this class
 * stays safe to load on a dedicated server.
 */
public final class CopycatSignNetwork {

    private CopycatSignNetwork() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(CopycatSignNetwork::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // .optional(): ohne dieses Flag würde ein Client ohne unsere Mod (bzw. mit älterer Version)
        // grundsätzlich vom Server abgelehnt.
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(ImageUploadPayload.TYPE, ImageUploadPayload.STREAM_CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> ImageUploadPayload.handle(payload, (ServerPlayer) ctx.player())));
        registrar.playToServer(ImageRequestPayload.TYPE, ImageRequestPayload.STREAM_CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> ImageRequestPayload.handle(payload, (ServerPlayer) ctx.player())));
        registrar.playToServer(PictureEditPayload.TYPE, PictureEditPayload.STREAM_CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> PictureEditPayload.handle(payload, (ServerPlayer) ctx.player())));
        registrar.playToServer(PictureThicknessPayload.TYPE, PictureThicknessPayload.STREAM_CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> PictureThicknessPayload.handle(payload, (ServerPlayer) ctx.player())));

        registrar.playToClient(OpenPictureEditorPayload.TYPE, OpenPictureEditorPayload.STREAM_CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> ClientPictureBridge.setPendingOpen(payload)));
        registrar.playToClient(ImageUploadResultPayload.TYPE, ImageUploadResultPayload.STREAM_CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> ClientPictureBridge.setPendingUploadResult(payload)));
        registrar.playToClient(ImageDataResponsePayload.TYPE, ImageDataResponsePayload.STREAM_CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> ClientPictureBridge.queueImageData(payload)));
    }
}
