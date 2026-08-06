package com.copycatsign.image;

import com.copycatsign.CopycatSign;
import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Per-world metadata for every custom picture players have uploaded (see F3.1 in ROADMAP.md),
 * mirroring Immersive Paintings' ServerPaintingManager: metadata lives in this SavedData, the actual
 * PNG bytes live in a flat-file ImageFileCache next to the world save (uploads can be a few MB each -
 * way too big to want round-tripped through NBT on every world save).
 *
 * Images are keyed by content hash (SHA-256 of the encoded PNG, see ImageUploadPayload), not by a
 * random id - two players uploading the same picture, or the same player re-uploading after an edit
 * that didn't actually change the image, both collapse onto one stored copy for free.
 */
public class ServerImageStore extends SavedData {

    private static final SavedData.Factory<ServerImageStore> FACTORY =
        new SavedData.Factory<>(ServerImageStore::new, ServerImageStore::load);
    private static final Codec<Map<String, ImageMetadata>> CODEC =
        Codec.unboundedMap(Codec.STRING, ImageMetadata.CODEC);

    private final Map<String, ImageMetadata> images = new HashMap<>();

    public static ServerImageStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, CopycatSign.MOD_ID + "_images");
    }

    public static ImageFileCache fileCache(MinecraftServer server) {
        return new ImageFileCache(server.getWorldPath(LevelResource.ROOT).resolve("copycatsign/images"));
    }

    public Optional<ImageMetadata> get(String hash) {
        return Optional.ofNullable(images.get(hash));
    }

    public void put(ImageMetadata metadata) {
        images.put(metadata.hash(), metadata);
        setDirty(true);
    }

    private static ServerImageStore load(CompoundTag tag, HolderLookup.Provider registries) {
        ServerImageStore store = new ServerImageStore();
        CODEC.parse(NbtOps.INSTANCE, tag.getCompound("images")).result().ifPresent(store.images::putAll);
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CODEC.encodeStart(NbtOps.INSTANCE, images).result().ifPresent(encoded -> tag.put("images", encoded));
        return tag;
    }
}
