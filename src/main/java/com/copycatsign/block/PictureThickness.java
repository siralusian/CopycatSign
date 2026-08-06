package com.copycatsign.block;

import net.minecraft.util.StringRepresentable;

/**
 * How thick (in 0-16 world units) a picture block's plate is. A blockstate property (2026-08-05
 * refactor) rather than a constructor parameter tied to a separate registered block per thickness -
 * collision shape and model selection both read it straight off the BlockState, the same way
 * {@link PicturePosition} already works, so a future GUI can change it with a plain
 * {@code state.setValue(THICKNESS, ...)} instead of swapping the block entirely.
 */
public enum PictureThickness implements StringRepresentable {
    VERY_THIN("very_thin", 1),
    THIN("thin", 2),
    MEDIUM("medium", 4),
    THICK("thick", 6);

    private final String serializedName;
    private final int pixels;

    PictureThickness(String serializedName, int pixels) {
        this.serializedName = serializedName;
        this.pixels = pixels;
    }

    public int pixels() {
        return pixels;
    }

    /** The models/block/pictures/&lt;sign&gt;&lt;suffix&gt;/ folder suffix for this thickness. */
    public String modelSuffix() {
        return this == THIN ? "" : "_" + serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
