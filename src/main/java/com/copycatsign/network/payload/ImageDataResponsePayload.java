package com.copycatsign.network.payload;

import com.copycatsign.CopycatSign;
import com.copycatsign.network.ChunkSender;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server->client reply to an ImageRequestPayload: the requested picture's PNG bytes, chunked the same
 * way ImageUploadPayload chunks an upload. totalSegments == 0 (with empty data) means "no such image
 * is stored" - the client should fall back to its placeholder rather than wait forever.
 */
public record ImageDataResponsePayload(String hash, byte[] data, int segment, int totalSegments) implements CustomPacketPayload {

    public static final Type<ImageDataResponsePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CopycatSign.MOD_ID, "image_data_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ImageDataResponsePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, ImageDataResponsePayload::hash,
        ByteBufCodecs.BYTE_ARRAY, ImageDataResponsePayload::data,
        ByteBufCodecs.INT, ImageDataResponsePayload::segment,
        ByteBufCodecs.INT, ImageDataResponsePayload::totalSegments,
        ImageDataResponsePayload::new
    );

    @Override
    public Type<ImageDataResponsePayload> type() {
        return TYPE;
    }

    public static void send(ServerPlayer player, String hash, byte[] data) {
        if (data == null) {
            PacketDistributor.sendToPlayer(player, new ImageDataResponsePayload(hash, new byte[0], 0, 0));
            return;
        }
        int total = ChunkSender.totalSegments(data);
        for (int segment = 0; segment < total; segment++) {
            PacketDistributor.sendToPlayer(player, new ImageDataResponsePayload(hash, ChunkSender.segment(data, segment), segment, total));
        }
    }
}
