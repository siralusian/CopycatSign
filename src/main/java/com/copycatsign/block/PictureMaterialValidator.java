package com.copycatsign.block;

import com.copycatsign.CopycatSign;
import com.copycatsign.CopycatSignConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Which blocks a player may apply as a sign's back/edge material, following the same 4-step
 * acceptance rules as Create FramedBlocks' BlockCamoContainerFactory#isValidBlock (chosen over
 * Create Copycat's simpler allow/deny pair per the user's explicit request):
 * 1. reject our own picture blocks (no self-referential material)
 * 2. reject anything on the blacklist tag
 * 3. reject blocks with a BlockEntity unless the server config allows it or it's whitelisted
 * 4. reject non-solid-render blocks unless they're on the frameable-whitelist tag
 */
public final class PictureMaterialValidator {

    public static final TagKey<Block> MATERIAL_BLACKLIST =
        blockTag("material_blacklisted");
    public static final TagKey<Block> MATERIAL_BLOCK_ENTITY_WHITELIST =
        blockTag("material_blockentity_whitelisted");
    public static final TagKey<Block> MATERIAL_FRAMEABLE =
        blockTag("material_frameable");

    private PictureMaterialValidator() {
    }

    public static boolean isValid(BlockState candidate, BlockGetter level, BlockPos pos, @Nullable Player player) {
        Block block = candidate.getBlock();
        if (block instanceof AbstractPictureBlock) {
            return false;
        }
        if (candidate.is(MATERIAL_BLACKLIST)) {
            return false;
        }
        if (candidate.hasBlockEntity() && !CopycatSignConfig.allowBlockEntityMaterials()
            && !candidate.is(MATERIAL_BLOCK_ENTITY_WHITELIST)) {
            return false;
        }
        if (!candidate.isSolidRender(level, pos) && !candidate.is(MATERIAL_FRAMEABLE)) {
            return false;
        }
        return true;
    }

    private static TagKey<Block> blockTag(String name) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(CopycatSign.MOD_ID, name));
    }
}
