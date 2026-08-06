package com.copycatsign.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Builds the sign's back-face texture: same silhouette as the uploaded picture (its alpha channel,
 * pixel for pixel), but filled with a material's own colors instead of the picture's - "paint over
 * whatever's opaque with the material, keep the shape" (2026-08-06 session). Mirrors what the
 * hardcoded Hogwarts/5972 signs' own "_backing" textures already do (wood grain masked to THEIR
 * artwork's silhouette, see ROADMAP.md) - except generated at runtime for an arbitrary uploaded
 * picture and an arbitrary chosen material instead of being pre-baked for one fixed pairing.
 */
final class PictureBackingTexture {

    private PictureBackingTexture() {
    }

    static NativeImage generate(NativeImage source, TextureAtlasSprite materialSprite) {
        int width = source.getWidth();
        int height = source.getHeight();
        NativeImage materialImage = materialSprite.contents().getOriginalImage();
        int tileWidth = materialSprite.contents().width();
        int tileHeight = materialSprite.contents().height();

        NativeImage result = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((source.getPixelRGBA(x, y) >>> 24 & 0xFF) == 0) {
                    result.setPixelRGBA(x, y, 0);
                    continue;
                }
                int materialColor = materialImage.getPixelRGBA(x % tileWidth, y % tileHeight);
                result.setPixelRGBA(x, y, materialColor | (0xFF << 24));
            }
        }
        return result;
    }
}
