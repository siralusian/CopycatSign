package com.copycatsign.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Custom placement for the flat picture blocks: always a single cell (see AbstractPictureBlock /
 * PictureBlockSingle - even a visually-oversized sign is just one block), placed flush
 * (PicturePosition.BACK) against whichever of the 6 faces was clicked. Bypasses BlockItem's default
 * placement only to add the "needs a solid backing face" check with a translated error message;
 * cycling the depth position afterwards is handled by AbstractPictureBlock.useItemOn, which
 * intercepts the click before this class ever sees it (see PASS_TO_DEFAULT_BLOCK_INTERACTION there
 * vs. here).
 */
public class PictureBlockItem extends BlockItem {

    public PictureBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        Direction facing = context.getClickedFace();
        BlockPos pos = level.getBlockState(context.getClickedPos()).canBeReplaced()
            ? context.getClickedPos()
            : context.getClickedPos().relative(facing);
        Direction upHint = facing.getAxis() == Direction.Axis.Y ? context.getHorizontalDirection() : Direction.NORTH;

        if (!level.getBlockState(pos).canBeReplaced() || !AbstractPictureBlock.isSupported(level, pos, facing)) {
            reportNoSpace(context.getPlayer());
            return InteractionResult.FAIL;
        }

        BlockState state = getBlock().defaultBlockState()
            .setValue(AbstractPictureBlock.FACING, facing)
            .setValue(AbstractPictureBlock.UP_HINT, upHint)
            .setValue(AbstractPictureBlock.POSITION, PicturePosition.BACK);
        level.setBlock(pos, state, Block.UPDATE_ALL);

        var soundType = state.getSoundType();
        level.playSound(context.getPlayer(), pos, soundType.getPlaceSound(),
            net.minecraft.sounds.SoundSource.BLOCKS, (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);

        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player != null) {
            stack.consume(1, player);
        } else {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    private static void reportNoSpace(Player player) {
        if (player != null) {
            player.displayClientMessage(Component.translatable("copycatsign.picture.no_space"), true);
        }
    }
}
