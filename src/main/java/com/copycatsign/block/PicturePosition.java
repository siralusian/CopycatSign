package com.copycatsign.block;

import net.minecraft.util.StringRepresentable;

/**
 * How far forward (away from the mounting face, along FACING) the thin picture plate currently
 * sits. Cycled by right-clicking with the item (see AbstractPictureBlock.useItemOn) - free, no
 * material cost, since it's just repositioning, not adding material.
 *
 * Stored as a FRACTION (0.0 = flush against the mount, 1.0 = as far forward as possible) rather
 * than a fixed pixel offset, because thickness is itself a separate blockstate property (see
 * PictureThickness) that can change independently - a fixed offset that worked for a 2px-thick
 * plate could push a thicker variant's box past the block's own 0-16 cell. {@link #offsetFor(int)}
 * always resolves to a value in [0, 16-thickness], so the plate's hitbox never leaves its own cell
 * regardless of thickness.
 */
public enum PicturePosition implements StringRepresentable {
    BACK("back", 0.0),
    BACK_MIDDLE("back_middle", 0.25),
    MIDDLE("middle", 0.5),
    FRONT_MIDDLE("front_middle", 0.75),
    FRONT("front", 1.0);

    private final String serializedName;
    private final double fraction;

    PicturePosition(String serializedName, double fraction) {
        this.serializedName = serializedName;
        this.fraction = fraction;
    }

    /** Depth offset in world units (0-16 scale), measured from the mounting face inward. */
    public int offsetFor(int thickness) {
        return (int) Math.round(fraction * (16 - thickness));
    }

    public PicturePosition next() {
        return switch (this) {
            case BACK -> BACK_MIDDLE;
            case BACK_MIDDLE -> MIDDLE;
            case MIDDLE -> FRONT_MIDDLE;
            case FRONT_MIDDLE -> FRONT;
            case FRONT -> BACK;
        };
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
