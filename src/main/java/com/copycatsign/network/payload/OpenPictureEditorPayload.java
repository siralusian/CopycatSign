package com.copycatsign.network.payload;

import com.copycatsign.CopycatSign;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server->client: open the picture editor for the sign at pos, pre-filled with its current state.
 * Sent in response to a right-click with an empty hand (see AbstractPictureBlock#useItemOn) - routed
 * through the server (rather than the client opening the screen directly on its own interaction
 * result) so AbstractPictureBlock, which is common code, never has to reference the client-only
 * Screen class. imageHash is an empty string for "no picture set yet" (StreamCodec's STRING_UTF8
 * can't carry null).
 */
public record OpenPictureEditorPayload(BlockPos pos, String imageHash, float panX, float panY, float zoom, String thickness) implements CustomPacketPayload {

    public static final Type<OpenPictureEditorPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CopycatSign.MOD_ID, "open_picture_editor"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenPictureEditorPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, OpenPictureEditorPayload::pos,
        ByteBufCodecs.STRING_UTF8, OpenPictureEditorPayload::imageHash,
        ByteBufCodecs.FLOAT, OpenPictureEditorPayload::panX,
        ByteBufCodecs.FLOAT, OpenPictureEditorPayload::panY,
        ByteBufCodecs.FLOAT, OpenPictureEditorPayload::zoom,
        ByteBufCodecs.STRING_UTF8, OpenPictureEditorPayload::thickness,
        OpenPictureEditorPayload::new
    );

    @Override
    public Type<OpenPictureEditorPayload> type() {
        return TYPE;
    }
}
