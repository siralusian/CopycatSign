package com.copycatsign.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime port of vanilla's ItemModelGenerator span-detection algorithm (see
 * net.minecraft.client.renderer.block.model.ItemModelGenerator#getSpans/#createSideElements) - the
 * same technique vanilla uses to give a flat 2D item icon its beveled 3D edge in hand, and the one the
 * Hogwarts/5972 signs' own edge geometry was generated with offline (a one-off PowerShell port, see
 * ROADMAP.md) at asset-build time. Custom uploaded pictures can't go through that offline step (the
 * content isn't known until upload time), so this runs the same algorithm at runtime instead, once per
 * uploaded image (see ClientImageManager, which caches the result by hash).
 *
 * A "span" is a maximal run of same-row (UP/DOWN) or same-column (LEFT/RIGHT) opaque pixels that sit
 * right on the silhouette's boundary in that direction - each one becomes one thin edge quad in
 * PictureBlockEntityRenderer, extruded through the sign's actual thickness and textured with the
 * matching strip of the source image (or the chosen edge material, tiled - see that class).
 */
public final class PictureSilhouette {

    public enum Facing {
        UP(Direction.UP, 0, -1),
        DOWN(Direction.DOWN, 0, 1),
        LEFT(Direction.EAST, -1, 0),
        RIGHT(Direction.WEST, 1, 0);

        private final Direction canonicalDirection;
        private final int dx;
        private final int dy;

        Facing(Direction canonicalDirection, int dx, int dy) {
            this.canonicalDirection = canonicalDirection;
            this.dx = dx;
            this.dy = dy;
        }

        /** The direction this span's face points in vanilla's own canonical (unrotated, SOUTH-facing)
         * model space - callers map this onto their own local right/up basis, see PictureGeometry3D. */
        public Direction canonicalDirection() {
            return canonicalDirection;
        }

        boolean isHorizontal() {
            return this == UP || this == DOWN;
        }
    }

    /** min/max/anchor are all raw pixel coordinates - min/max along the span's own run direction,
     * anchor the fixed row (UP/DOWN) or column (LEFT/RIGHT) it sits on. */
    public static final class Span {
        public final Facing facing;
        public int min;
        public int max;
        public final int anchor;

        private Span(Facing facing, int minMax, int anchor) {
            this.facing = facing;
            this.min = minMax;
            this.max = minMax;
            this.anchor = anchor;
        }

        private void expand(int pos) {
            if (pos < min) {
                min = pos;
            } else if (pos > max) {
                max = pos;
            }
        }
    }

    private PictureSilhouette() {
    }

    public static List<Span> computeSpans(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        List<Span> spans = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean opaque = !isTransparent(image, x, y, width, height);
                if (!opaque) {
                    continue;
                }
                for (Facing facing : Facing.values()) {
                    if (isTransparent(image, x + facing.dx, y + facing.dy, width, height)) {
                        createOrExpand(spans, facing, x, y);
                    }
                }
            }
        }
        return spans;
    }

    private static void createOrExpand(List<Span> spans, Facing facing, int x, int y) {
        int anchor = facing.isHorizontal() ? y : x;
        int pos = facing.isHorizontal() ? x : y;
        for (Span span : spans) {
            if (span.facing == facing && span.anchor == anchor) {
                span.expand(pos);
                return;
            }
        }
        spans.add(new Span(facing, pos, anchor));
    }

    private static boolean isTransparent(NativeImage image, int x, int y, int width, int height) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return true;
        }
        return (image.getPixelRGBA(x, y) >>> 24 & 0xFF) == 0;
    }
}
