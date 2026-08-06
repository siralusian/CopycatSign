package com.copycatsign.network.payload;

import com.copycatsign.CopycatSign;
import com.copycatsign.block.PictureBlockEntity;
import com.copycatsign.image.ServerImageStore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Client->server: apply a change made in the picture editor (see OpenPictureEditorPayload /
 * PictureEditorScreen) to the sign at pos - either a newly uploaded picture (imageHash non-empty) or
 * a pan/zoom adjustment on the picture it already has (imageHash empty means "keep the current one,
 * just update pan/zoom"). Re-validates server-side that pos still holds a PictureBlockEntity and that
 * the player is close enough to plausibly be looking at its own open editor - a client only ever
 * fabricates this payload through the screen it was sent via OpenPictureEditorPayload, but the server
 * must not trust position/range blindly regardless.
 */
public record PictureEditPayload(BlockPos pos, String imageHash, float panX, float panY, float zoom, boolean changeImage) implements CustomPacketPayload {

    private static final double MAX_EDIT_DISTANCE_SQ = 8.0 * 8.0;

    public static final Type<PictureEditPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CopycatSign.MOD_ID, "picture_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PictureEditPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, PictureEditPayload::pos,
        ByteBufCodecs.STRING_UTF8, PictureEditPayload::imageHash,
        ByteBufCodecs.FLOAT, PictureEditPayload::panX,
        ByteBufCodecs.FLOAT, PictureEditPayload::panY,
        ByteBufCodecs.FLOAT, PictureEditPayload::zoom,
        ByteBufCodecs.BOOL, PictureEditPayload::changeImage,
        PictureEditPayload::new
    );

    @Override
    public Type<PictureEditPayload> type() {
        return TYPE;
    }

    public static void handle(PictureEditPayload payload, ServerPlayer player) {
        if (player.distanceToSqr(payload.pos().getX() + 0.5, payload.pos().getY() + 0.5, payload.pos().getZ() + 0.5) > MAX_EDIT_DISTANCE_SQ) {
            return;
        }
        BlockEntity blockEntity = player.level().getBlockEntity(payload.pos());
        if (!(blockEntity instanceof PictureBlockEntity pictureBlockEntity)) {
            return;
        }
        if (payload.changeImage()) {
            if (payload.imageHash().isEmpty()) {
                pictureBlockEntity.setImage(null, 0, 0);
                return;
            }
            // Width/height come from the server's own record of the upload, never the client's claim -
            // a client only ever sends a hash it just got back from a successful ImageUploadResultPayload,
            // but that's exactly the kind of thing a modified client could lie about to warp this sign's
            // rendered size arbitrarily.
            ServerImageStore.get(player.getServer()).get(payload.imageHash()).ifPresent(metadata ->
                pictureBlockEntity.setImage(payload.imageHash(), metadata.width(), metadata.height()));
        } else {
            pictureBlockEntity.setPanAndZoom(payload.panX(), payload.panY(), payload.zoom());
        }
    }
}
