package com.copycatsign.client;

import com.copycatsign.CopycatSign;
import com.copycatsign.image.ImageFileCache;
import com.copycatsign.network.SegmentManager;
import com.copycatsign.network.payload.ImageDataResponsePayload;
import com.copycatsign.network.payload.ImageRequestPayload;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Client-side cache of custom pictures, keyed by content hash (see ServerImageStore). A picture a
 * PictureBlockEntityRenderer wants to show is very likely not loaded yet the first time it's asked
 * for - {@link #resolveTexture} returns null in that case and kicks off (at most once per hash) a
 * disk-cache check, then a server request if it's not on disk either. Registered textures live
 * outside the normal block atlas (DynamicTexture, not a TextureAtlasSprite), which is exactly why
 * PictureBlockEntityRenderer has to render this face itself instead of going through the normal
 * baked-model path (see PictureBakedModel's HAS_IMAGE suppression of that quad).
 */
public final class ClientImageManager {

    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();
    private static final Map<String, NativeImage> IMAGES = new HashMap<>();
    private static final Map<String, List<PictureSilhouette.Span>> SILHOUETTES = new HashMap<>();
    private static final Map<String, ResourceLocation> BACKING_TEXTURES = new HashMap<>();
    private static final Set<String> BACKING_REQUESTED = new HashSet<>();
    private static final Map<String, ResourceLocation> MATERIAL_SWATCHES = new HashMap<>();
    private static final Set<String> MATERIAL_SWATCH_REQUESTED = new HashSet<>();
    private static final Set<String> REQUESTED = new HashSet<>();
    private static final SegmentManager SEGMENTS = new SegmentManager();
    // Both callers of registerTexture (resolveTexture, called from the block entity renderer, and
    // handleResponse, called from a network payload's ctx.enqueueWork) run on the render thread.
    // NativeImage.read must NOT run there - it draws from LWJGL's small, fixed-size per-thread
    // MemoryStack, and decoding nested inside whatever MemoryStack usage the current render tick
    // already has in flight can exhaust that stack and crash with "OutOfMemoryError: Out of stack
    // space" (unrelated to JVM heap/-Xmx). Decode on this dedicated background thread instead; only
    // the GL-touching DynamicTexture registration afterwards needs to hop back to the render thread.
    private static final ExecutorService DECODE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "copycatsign-image-decode");
        thread.setDaemon(true);
        return thread;
    });
    private static ImageFileCache diskCache;

    private ClientImageManager() {
    }

    private static ImageFileCache diskCache() {
        if (diskCache == null) {
            diskCache = new ImageFileCache(Minecraft.getInstance().gameDirectory.toPath().resolve("copycatsign/imagecache"));
        }
        return diskCache;
    }

    /** Null means "not ready yet" - a request may already be in flight, check back next frame. */
    public static ResourceLocation resolveTexture(String hash) {
        ResourceLocation cached = TEXTURES.get(hash);
        if (cached != null) {
            return cached;
        }
        if (!REQUESTED.add(hash)) {
            return null;
        }

        Optional<byte[]> onDisk = diskCache().get(hash);
        if (onDisk.isPresent()) {
            // Decode/registration now happens off-thread (see registerTexture) and won't be ready
            // synchronously - fall through and return null, matching the "check back next frame"
            // contract already used for the server-request path below.
            registerTexture(hash, onDisk.get());
            return null;
        }

        PacketDistributor.sendToServer(new ImageRequestPayload(hash));
        return null;
    }

    public static void handleResponse(ImageDataResponsePayload payload) {
        if (payload.totalSegments() == 0) {
            CopycatSign.LOGGER.warn("server has no image stored for hash {}", payload.hash());
            return;
        }
        SEGMENTS.handleSegmentedPayload(payload.hash(), payload.data(), payload.segment(), payload.totalSegments())
            .ifPresent(bytes -> {
                diskCache().set(payload.hash(), bytes);
                registerTexture(payload.hash(), bytes);
            });
    }

    /** Empty (never null) once decode has finished for this hash - a picture with no transparent
     * pixels at all legitimately has zero silhouette spans (its outline is just the block's own edge,
     * already covered by PICTURE_5972's plain edge geometry via PictureBakedModel). Still-loading is
     * indistinguishable from "no spans" here, same as resolveTexture's null-means-not-ready contract -
     * PictureBlockEntityRenderer already bails out separately while resolveTexture returns null. */
    public static List<PictureSilhouette.Span> resolveSilhouette(String hash) {
        return SILHOUETTES.getOrDefault(hash, List.of());
    }

    /** The sign's back face: same alpha shape as the uploaded picture (so it doesn't show through the
     * gaps a real cutout picture would have), but painted with the given material instead of the
     * picture's own colors - see PictureBackingTexture for how that's built. Null while still
     * generating (same "check back next frame" contract as {@link #resolveTexture}) - materialKey
     * distinguishes cached results per material choice (or the shared default), since the same picture
     * can be backed differently on different signs. */
    public static ResourceLocation resolveBackingTexture(String hash, String materialKey, TextureAtlasSprite materialSprite) {
        String cacheKey = hash + '|' + materialKey;
        ResourceLocation cached = BACKING_TEXTURES.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        NativeImage source = IMAGES.get(hash);
        if (source == null || !BACKING_REQUESTED.add(cacheKey)) {
            return null;
        }

        DECODE_EXECUTOR.execute(() -> {
            NativeImage backing = PictureBackingTexture.generate(source, materialSprite);
            Minecraft.getInstance().execute(() -> {
                // ResourceLocation paths can't contain ':' - materialKey is normally a registry name
                // like "minecraft:stone", so building the path straight from cacheKey (which embeds it)
                // produced an invalid path before this replace, silently corrupting which cached entry
                // got looked up/overwritten for anything but the colon-free "default" key.
                String safeKey = cacheKey.replace('|', '/').replace(':', '_');
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CopycatSign.MOD_ID, "backing/" + safeKey);
                Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(backing));
                BACKING_TEXTURES.put(cacheKey, id);
            });
        });
        return null;
    }

    /** A single un-mipmapped tile of the given material, as its own standalone texture - see
     * PictureMaterialSwatch's javadoc for why: sampling the material straight from the block atlas (as
     * PictureBakedModel's remapQuad does) looks fine on quads at least a texel or so across, but the
     * silhouette edge quads this is for are often far thinner than that, and the atlas's mipmapping
     * picks a heavily-blurred LOD for them, "smearing" the material rather than showing its actual
     * pattern (the sign's BACK face never had this problem - it was already its own un-mipmapped
     * DynamicTexture, see resolveBackingTexture). Null while still generating, same contract as the
     * other resolve* methods here. materialKey both identifies the cache entry and avoids re-copying
     * the same material's pixels for every sign that happens to use it. */
    public static ResourceLocation resolveMaterialSwatch(String materialKey, TextureAtlasSprite materialSprite) {
        ResourceLocation cached = MATERIAL_SWATCHES.get(materialKey);
        if (cached != null) {
            return cached;
        }
        if (!MATERIAL_SWATCH_REQUESTED.add(materialKey)) {
            return null;
        }

        DECODE_EXECUTOR.execute(() -> {
            NativeImage swatch = PictureMaterialSwatch.generate(materialSprite);
            Minecraft.getInstance().execute(() -> {
                String safeKey = materialKey.replace(':', '_');
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CopycatSign.MOD_ID, "material_swatch/" + safeKey);
                Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(swatch));
                MATERIAL_SWATCHES.put(materialKey, id);
            });
        });
        return null;
    }

    private static void registerTexture(String hash, byte[] pngBytes) {
        DECODE_EXECUTOR.execute(() -> {
            NativeImage image;
            try {
                image = NativeImageDecoder.read(pngBytes);
            } catch (IOException e) {
                CopycatSign.LOGGER.error("could not decode image {}", hash, e);
                return;
            }
            // The span scan (see PictureSilhouette) is O(width*height) - cheap for one image, but not
            // something to redo every frame, so it runs once here (already off the render thread)
            // alongside the decode, and PictureBlockEntityRenderer just looks up the cached result.
            List<PictureSilhouette.Span> spans = PictureSilhouette.computeSpans(image);
            Minecraft.getInstance().execute(() -> {
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CopycatSign.MOD_ID, "custom/" + hash);
                Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
                TEXTURES.put(hash, id);
                IMAGES.put(hash, image);
                SILHOUETTES.put(hash, spans);
            });
        });
    }
}
