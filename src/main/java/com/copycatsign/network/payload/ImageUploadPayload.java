package com.copycatsign.network.payload;

import com.copycatsign.CopycatSign;
import com.copycatsign.CopycatSignConfig;
import com.copycatsign.image.ImageMetadata;
import com.copycatsign.image.ServerImageStore;
import com.copycatsign.network.SegmentManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Client->server chunked upload of a custom picture's raw PNG bytes (see ROADMAP.md Feature 3 /
 * ServerImageStore). Ported from Immersive Paintings' ImageUploadPayload: a single upload is split
 * client-side into several of these (packets have a size cap well below what an image can reach),
 * reassembled here via SegmentManager, then decoded/validated/stored once the last segment arrives.
 *
 * Not wired to any UI trigger yet (that's F3.4's job) - this class and its handler are complete and
 * independently testable network plumbing.
 */
public record ImageUploadPayload(byte[] data, int segment, int totalSegments) implements CustomPacketPayload {

    public static final Type<ImageUploadPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CopycatSign.MOD_ID, "image_upload"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ImageUploadPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BYTE_ARRAY, ImageUploadPayload::data,
        ByteBufCodecs.INT, ImageUploadPayload::segment,
        ByteBufCodecs.INT, ImageUploadPayload::totalSegments,
        ImageUploadPayload::new
    );

    private static final SegmentManager SEGMENTS = new SegmentManager();

    @Override
    public Type<ImageUploadPayload> type() {
        return TYPE;
    }

    public static void handle(ImageUploadPayload payload, ServerPlayer player) {
        String key = player.getStringUUID();
        SEGMENTS.handleSegmentedPayload(key, payload.data(), payload.segment(), payload.totalSegments())
            .ifPresent(bytes -> finish(player, bytes));
    }

    private static void finish(ServerPlayer player, byte[] bytes) {
        if (bytes.length > CopycatSignConfig.maxImageBytes()) {
            CopycatSign.LOGGER.warn("rejected image upload from {}: {} bytes exceeds the configured limit", player.getGameProfile().getName(), bytes.length);
            ImageUploadResultPayload.sendFailure(player, "copycatsign.upload.too_large");
            return;
        }

        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            image = null;
        }
        if (image == null) {
            CopycatSign.LOGGER.warn("rejected image upload from {}: not a readable image", player.getGameProfile().getName());
            ImageUploadResultPayload.sendFailure(player, "copycatsign.upload.invalid_image");
            return;
        }

        int maxDimension = CopycatSignConfig.maxImageDimension();
        if (image.getWidth() > maxDimension || image.getHeight() > maxDimension) {
            CopycatSign.LOGGER.warn("rejected image upload from {}: {}x{} exceeds the configured {}px limit",
                player.getGameProfile().getName(), image.getWidth(), image.getHeight(), maxDimension);
            ImageUploadResultPayload.sendFailure(player, "copycatsign.upload.too_big_dimensions");
            return;
        }

        String hash = sha256Hex(bytes);
        ServerImageStore store = ServerImageStore.get(player.server);
        if (store.get(hash).isEmpty()) {
            ServerImageStore.fileCache(player.server).set(hash, bytes);
            store.put(new ImageMetadata(hash, image.getWidth(), image.getHeight(), player.getUUID()));
        }
        ImageUploadResultPayload.sendSuccess(player, hash, image.getWidth(), image.getHeight());
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
