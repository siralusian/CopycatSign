package com.copycatsign.block;

import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/**
 * The two independently chosen materials attached to a picture block's ModelData - see
 * PictureBakedModel for how they get rendered. BACK_MATERIAL covers the flat face opposite FACING,
 * EDGE_MATERIAL covers everything perpendicular to FACING (the silhouette-extrusion "step" quads).
 * The face matching FACING itself (the picture) is never affected by either slot.
 */
public final class PictureMaterialProperty {

    public static final ModelProperty<BlockState> BACK_MATERIAL = new ModelProperty<>();
    public static final ModelProperty<BlockState> EDGE_MATERIAL = new ModelProperty<>();

    private PictureMaterialProperty() {
    }
}
