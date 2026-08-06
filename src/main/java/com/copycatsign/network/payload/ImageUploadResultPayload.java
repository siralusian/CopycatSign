package com.copycatsign.network.payload;

import com.copycatsign.CopycatSign;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server->client reply to an ImageUploadPayload once every segment has arrived: either the assigned
 * content hash (see ServerImageStore) plus the image's actual pixel dimensions, or a translation-key
 * explaining why the upload was rejected. The upload GUI (F3.4) is the intended consumer, but this
 * payload and its send helpers are independently complete.
 */
public record ImageUploadResultPayload(boolean success, String hashOrErrorKey, int width, int height) implements CustomPacketPayload {

    public static final Type<ImageUploadResultPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CopycatSign.MOD_ID, "image_upload_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ImageUploadResultPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, ImageUploadResultPayload::success,
        ByteBufCodecs.STRING_UTF8, ImageUploadResultPayload::hashOrErrorKey,
        ByteBufCodecs.INT, ImageUploadResultPayload::width,
        ByteBufCodecs.INT, ImageUploadResultPayload::height,
        ImageUploadResultPayload::new
    );

    @Override
    public Type<ImageUploadResultPayload> type() {
        return TYPE;
    }

    public static void sendSuccess(ServerPlayer player, String hash, int width, int height) {
        PacketDistributor.sendToPlayer(player, new ImageUploadResultPayload(true, hash, width, height));
    }

    public static void sendFailure(ServerPlayer player, String translationKey) {
        PacketDistributor.sendToPlayer(player, new ImageUploadResultPayload(false, translationKey, 0, 0));
    }
}
