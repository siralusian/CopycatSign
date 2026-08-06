package com.copycatsign.image;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

/** Everything about an uploaded custom picture except its actual pixel data (see ImageFileCache). */
public record ImageMetadata(String hash, int width, int height, UUID uploader) {

    public static final Codec<ImageMetadata> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("hash").forGetter(ImageMetadata::hash),
        Codec.INT.fieldOf("width").forGetter(ImageMetadata::width),
        Codec.INT.fieldOf("height").forGetter(ImageMetadata::height),
        UUIDUtil.CODEC.fieldOf("uploader").forGetter(ImageMetadata::uploader)
    ).apply(instance, ImageMetadata::new));
}
