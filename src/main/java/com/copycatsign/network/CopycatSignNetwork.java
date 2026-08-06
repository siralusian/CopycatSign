package com.copycatsign.network;

import com.copycatsign.network.payload.ImageRequestPayload;
import com.copycatsign.network.payload.ImageUploadPayload;
import com.copycatsign.network.payload.PictureEditPayload;
import com.copycatsign.network.payload.PictureThicknessPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers the server-bound network payloads (the ones the server needs to be able to DECODE, i.e.
 * ones it receives). The client-bound payloads (ImageUploadResultPayload, ImageDataResponsePayload,
 * OpenPictureEditorPayload) are registered separately in CopycatSignClient, which is Dist.CLIENT-gated
 * - it must not be touched from here, since a direct reference to that class would force the JVM to
 * load it (and whatever client-only classes it touches) on a dedicated server too. The server can
 * still SEND those without registering them here: sending only needs the payload's own StreamCodec
 * (a plain static field, unrelated to registration), registration is specifically about the
 * *receiving* side knowing how to decode/handle an incoming payload of that type.
 */
public final class CopycatSignNetwork {

    private CopycatSignNetwork() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(CopycatSignNetwork::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(ImageUploadPayload.TYPE, ImageUploadPayload.STREAM_CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> ImageUploadPayload.handle(payload, (ServerPlayer) ctx.player())));
        registrar.playToServer(ImageRequestPayload.TYPE, ImageRequestPayload.STREAM_CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> ImageRequestPayload.handle(payload, (ServerPlayer) ctx.player())));
        registrar.playToServer(PictureEditPayload.TYPE, PictureEditPayload.STREAM_CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> PictureEditPayload.handle(payload, (ServerPlayer) ctx.player())));
        registrar.playToServer(PictureThicknessPayload.TYPE, PictureThicknessPayload.STREAM_CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> PictureThicknessPayload.handle(payload, (ServerPlayer) ctx.player())));
    }
}
