package com.copycatsign.block;

import net.neoforged.neoforge.client.model.data.ModelProperty;

/**
 * Whether this sign has a custom uploaded picture (see PictureBlockEntity/ROADMAP.md Feature 3).
 * PictureBakedModel reads this to suppress its own static "#front" quad when true, since
 * PictureBlockEntityRenderer draws the custom picture there instead (a DynamicTexture, which isn't
 * part of the block atlas the baked-quad path samples from).
 */
public final class PictureImageProperty {

    public static final ModelProperty<Boolean> HAS_IMAGE = new ModelProperty<>();

    private PictureImageProperty() {
    }
}
