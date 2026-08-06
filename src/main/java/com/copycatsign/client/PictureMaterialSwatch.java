package com.copycatsign.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Copies a single tile of a material's own source texture (its first animation frame, if it has any -
 * TextureAtlasSprite#contents#getOriginalImage is the sprite's own pre-stitching image, not a crop out
 * of the shared atlas) into its own small standalone image - see
 * ClientImageManager#resolveMaterialSwatch for why: sampling the material from the ATLAS (as
 * PictureBakedModel's remapQuad does) goes through the atlas's mipmap chain, which blurs it badly on
 * the very thin silhouette edge quads PictureBlockEntityRenderer draws (2026-08-06 session) - a fresh,
 * non-mipmapped DynamicTexture (the same kind already used for the picture itself and for
 * PictureBackingTexture's back face) doesn't have that problem.
 */
final class PictureMaterialSwatch {

    private PictureMaterialSwatch() {
    }

    static NativeImage generate(TextureAtlasSprite sprite) {
        NativeImage atlasImage = sprite.contents().getOriginalImage();
        int width = sprite.contents().width();
        int height = sprite.contents().height();

        NativeImage result = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                result.setPixelRGBA(x, y, atlasImage.getPixelRGBA(x, y));
            }
        }
        return result;
    }
}
