package com.copycatsign.network.payload;

import com.copycatsign.CopycatSign;
import com.copycatsign.image.ServerImageStore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Client->server request for a custom picture's actual pixel data, identified by content hash (see
 * ImageUploadPayload/ServerImageStore). The client only ever has the hash for pictures it has seen
 * referenced (e.g. a nearby sign's BlockEntity, once F3.4's GUI can assign one) - not the bytes, which
 * are fetched on demand rather than pushed proactively to every player.
 */
public record ImageRequestPayload(String hash) implements CustomPacketPayload {

    public static final Type<ImageRequestPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CopycatSign.MOD_ID, "image_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ImageRequestPayload> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ImageRequestPayload::hash, ImageRequestPayload::new);

    @Override
    public Type<ImageRequestPayload> type() {
        return TYPE;
    }

    public static void handle(ImageRequestPayload payload, ServerPlayer player) {
        String hash = payload.hash();
        Optional<byte[]> data = ServerImageStore.get(player.server).get(hash).isPresent()
            ? ServerImageStore.fileCache(player.server).get(hash)
            : Optional.empty();
        ImageDataResponsePayload.send(player, hash, data.orElse(null));
    }
}
