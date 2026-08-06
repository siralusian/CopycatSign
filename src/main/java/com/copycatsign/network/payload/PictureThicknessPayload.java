package com.copycatsign.network.payload;

import com.copycatsign.CopycatSign;
import com.copycatsign.block.AbstractPictureBlock;
import com.copycatsign.block.PictureThickness;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Client->server: cycle the sign's thickness from the picture editor screen (see
 * PictureEditorScreen/ROADMAP.md - previously only reachable by re-placing with a different item
 * variant, back when thickness meant separate registered blocks; now a blockstate property like
 * POSITION, so the editor can just cycle it in place the same way AbstractPictureBlock#useItemOn
 * already cycles POSITION). Re-validates server-side that pos still holds one of these blocks and the
 * player is close enough, matching PictureEditPayload's own trust model.
 */
public record PictureThicknessPayload(BlockPos pos, String thickness) implements CustomPacketPayload {

    private static final double MAX_EDIT_DISTANCE_SQ = 8.0 * 8.0;

    public static final Type<PictureThicknessPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CopycatSign.MOD_ID, "picture_thickness"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PictureThicknessPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, PictureThicknessPayload::pos,
        ByteBufCodecs.STRING_UTF8, PictureThicknessPayload::thickness,
        PictureThicknessPayload::new
    );

    @Override
    public Type<PictureThicknessPayload> type() {
        return TYPE;
    }

    public static void handle(PictureThicknessPayload payload, ServerPlayer player) {
        if (player.distanceToSqr(payload.pos().getX() + 0.5, payload.pos().getY() + 0.5, payload.pos().getZ() + 0.5) > MAX_EDIT_DISTANCE_SQ) {
            return;
        }
        Level level = player.level();
        BlockState state = level.getBlockState(payload.pos());
        if (!(state.getBlock() instanceof AbstractPictureBlock)) {
            return;
        }
        PictureThickness thickness;
        try {
            thickness = PictureThickness.valueOf(payload.thickness());
        } catch (IllegalArgumentException e) {
            return;
        }
        level.setBlock(payload.pos(), state.setValue(AbstractPictureBlock.THICKNESS, thickness), Block.UPDATE_ALL);
    }
}
