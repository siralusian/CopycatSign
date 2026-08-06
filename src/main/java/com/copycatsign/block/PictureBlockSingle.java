package com.copycatsign.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * A picture block with exactly one physical cell (hitbox + placement) - used for every sign
 * variant, including visually-oversized ones (e.g. the Hogwarts Express sign): the extra visual
 * size lives entirely in the blockstate model, not in Java. There are no sibling cells. A
 * PictureBlockEntity stores the optional player-chosen back/edge material (see AbstractPictureBlock
 * and PictureBakedModel).
 */
public class PictureBlockSingle extends AbstractPictureBlock {

    public PictureBlockSingle(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(UP_HINT, Direction.NORTH)
            .setValue(POSITION, PicturePosition.BACK)
            .setValue(THICKNESS, PictureThickness.THIN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, UP_HINT, POSITION, THICKNESS);
    }
}
