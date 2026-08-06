package com.copycatsign.block;

import com.copycatsign.network.payload.OpenPictureEditorPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Shared mechanics for the "flat picture" blocks: a single, always-1x1-hitbox block whose model
 * may render much larger than its own cell (see PictureBlockSingle for the oversized-model use, or
 * a normal same-size use) or exactly its own cell. Right-clicking with its own item cycles WHERE
 * along the depth axis the plate sits (back/middle/front, see PicturePosition) - free, since it's
 * repositioning, not adding material. This lets the same sign be mounted flush against a flat wall,
 * or pulled forward to sit against an oversized/curved surface (e.g. a Create train boiler) without
 * needing a different block.
 *
 * Ported from CobbleCompanion-Everything's com.cobblecompanion.pictures package (session of
 * 2026-08-03/04) as the starting point for this standalone mod - see ROADMAP.md for what's still
 * planned on top of this (in-game image upload, position/zoom).
 *
 * Thickness is a blockstate property (see {@link PictureThickness}, 2026-08-05 refactor) rather
 * than a constructor parameter tied to a separate registered block per thickness - now that the
 * planned image-upload GUI needs to change it live anyway, a plain {@code state.setValue(THICKNESS,
 * ...)} (matching how POSITION already works) is simpler than swapping the whole block, and cuts a
 * lot of registration boilerplate (one block per motif instead of one per motif-and-thickness).
 *
 * FACING = the direction the picture faces (away from the surface it's mounted on), all 6
 * directions allowed. UP_HINT only matters when FACING is vertical (floor/ceiling mount, where
 * there's no inherent "up" in the picture plane) - it records which horizontal direction was
 * chosen as the picture's "up" edge at placement time (see PictureBlockItem). For horizontal
 * FACING it is always NORTH and simply ignored.
 */
public abstract class AbstractPictureBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final DirectionProperty UP_HINT = DirectionProperty.create("up_hint", Direction.Plane.HORIZONTAL);
    public static final EnumProperty<PicturePosition> POSITION = EnumProperty.create("position", PicturePosition.class);
    public static final EnumProperty<PictureThickness> THICKNESS = EnumProperty.create("thickness", PictureThickness.class);

    protected AbstractPictureBlock(Properties properties) {
        super(properties);
    }

    /** The horizontal-plane direction that acts as "up" for this block's picture content. */
    public static Direction resolveLocalUp(Direction facing, Direction upHint) {
        return facing.getAxis() == Direction.Axis.Y ? upHint : Direction.UP;
    }

    /**
     * The direction that acts as "right" for this block's picture content, from the point of view
     * of someone standing in front of it looking at it.
     *
     * For a horizontal (wall) mount the viewer's own look direction is facing.getOpposite() (they
     * stand on the far side and look back toward the wall), and "their right hand" while looking in
     * direction D is D.getClockWise() - so the combined formula is facing.getOpposite().getClockWise(),
     * i.e. facing.getCounterClockWise(). Floor and ceiling mounts both use up_hint.getCounterClockWise()
     * too - up_hint IS the placer's own look direction there (see resolveLocalUp), which already
     * plays the same role facing does for a wall mount. (Both directions confirmed by live testing.)
     */
    public static Direction resolveLocalRight(Direction facing, Direction localUp) {
        if (facing.getAxis() == Direction.Axis.Y) {
            return localUp.getCounterClockWise();
        }
        return facing.getCounterClockWise();
    }

    public static boolean isSupported(LevelReader level, BlockPos pos, Direction facing) {
        BlockPos supportPos = pos.relative(facing.getOpposite());
        BlockState supportState = level.getBlockState(supportPos);
        return supportState.isFaceSturdy(level, supportPos, facing);
    }

    /**
     * The plate's collision/outline box - always inside this block's own 0-16 cell (even at the
     * FRONT position), matching PicturePosition's capped offsets. The VISUAL model may extend far
     * beyond this for oversized signs, but that's rendering only, never collision.
     */
    public static VoxelShape computeShape(Direction facing, PicturePosition position, PictureThickness thickness) {
        int pixels = thickness.pixels();
        int offset = position.offsetFor(pixels);
        boolean positive = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        double back = positive ? offset : 16 - offset;
        double front = positive ? offset + pixels : 16 - offset - pixels;
        double min = Math.min(back, front);
        double max = Math.max(back, front);
        return switch (facing.getAxis()) {
            case X -> Block.box(min, 0, 0, max, 16, 16);
            case Y -> Block.box(0, min, 0, 16, max, 16);
            case Z -> Block.box(0, 0, min, 16, 16, max);
        };
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return computeShape(state.getValue(FACING), state.getValue(POSITION), state.getValue(THICKNESS));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return computeShape(state.getValue(FACING), state.getValue(POSITION), state.getValue(THICKNESS));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return isSupported(level, pos, state.getValue(FACING));
    }

    /**
     * Right-clicking a placed picture with its own item cycles its depth position (back -> middle
     * -> front -> back). Right-clicking with a DIFFERENT block item tries to apply it as a material
     * (Create-Copycat-style, see PictureMaterialValidator for the accepted-block rules and
     * PictureBlockEntity/PictureBakedModel for how it actually gets rendered) - which hand it's held
     * in decides which slot: off-hand targets the edge material, main-hand (the common case) targets
     * the back material. (Sneak was considered but rejected: it's vanilla's convention for "place the
     * block instead of interacting" on interactive blocks, and re-using it here would fight that
     * expectation. Off-hand needs no extra networking either, since InteractionHand is already part
     * of the normal interact packet - unlike sneak, there's no vanilla-synced "is Ctrl held" flag to
     * read server-side without adding one ourselves.) Any other item (or an invalid material) falls
     * through to normal block interaction.
     *
     * Right-clicking with an EMPTY hand instead opens the picture editor (see ROADMAP.md Feature 3 /
     * PictureEditorScreen) - routed through the server (which sends OpenPictureEditorPayload back to
     * the clicking player) rather than the client opening the screen straight from its own call here,
     * since this class is common code and must never reference the client-only Screen class directly.
     *
     * Vanilla calls this once per hand (main hand first, then off-hand only if main hand returned
     * PASS) - an empty MAIN hand must not immediately claim the interaction if the OFF hand holds a
     * material, or "off-hand material -> edge slot" above could never be reached at all (main hand's
     * empty check would fire first every time and consume the click before off-hand is ever tried).
     * So an empty main hand only opens the editor if the off hand is ALSO empty; otherwise it passes
     * through and lets the off-hand call (this same method, called again with hand=OFF_HAND) decide.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) {
            if (hand == InteractionHand.MAIN_HAND && !player.getOffhandItem().isEmpty()) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof PictureBlockEntity blockEntity) {
                String hash = blockEntity.getImageHash();
                PacketDistributor.sendToPlayer(serverPlayer, new OpenPictureEditorPayload(
                    pos, hash == null ? "" : hash, blockEntity.getPanX(), blockEntity.getPanY(), blockEntity.getZoom(),
                    state.getValue(THICKNESS).name()));
            }
            return ItemInteractionResult.SUCCESS;
        }

        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (blockItem.getBlock() == this) {
            if (level.isClientSide) {
                return ItemInteractionResult.SUCCESS;
            }
            level.setBlock(pos, state.setValue(POSITION, state.getValue(POSITION).next()), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 1.1F);
            return ItemInteractionResult.SUCCESS;
        }

        BlockState candidateMaterial = blockItem.getBlock().defaultBlockState();
        if (!PictureMaterialValidator.isValid(candidateMaterial, level, pos, player)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof PictureBlockEntity blockEntity) {
            if (hand == InteractionHand.OFF_HAND) {
                blockEntity.setEdgeMaterial(candidateMaterial);
            } else {
                blockEntity.setBackMaterial(candidateMaterial);
            }
            level.playSound(null, pos, candidateMaterial.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.75F);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PictureBlockEntity(PictureBlockEntities.PICTURE.get(), pos, state);
    }
}
