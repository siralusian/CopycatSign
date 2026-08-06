package com.copycatsign.client;

import com.copycatsign.block.AbstractPictureBlock;
import com.copycatsign.block.PictureImageProperty;
import com.copycatsign.block.PictureMaterialProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Re-textures a sign's back and edge quads with the player-chosen materials (see
 * PictureBlockEntity/PictureMaterialProperty). This works because the quads' SHAPE never changes
 * with the chosen material, unlike Create's Copycat blocks, so we don't need their quad-cropping
 * machinery, just a sprite swap.
 *
 * The UV for each remapped quad is computed fresh from its own vertex XYZ (wrapped every 1 block, the
 * same way a normal multi-block wall built from that material would tile), NOT carried over from the
 * original "#front"/"#backing" UV. That distinction matters a lot here: our edge quads' original UVs
 * are deliberately tiny, precisely-positioned slivers (the ported vanilla ItemModelGenerator samples
 * a single matching pixel-column/row out of the large source picture per silhouette step) - preserving
 * that sliver's fractional position on an unrelated 16x16 material sprite just stretches one pixel of
 * it across the whole quad. Re-deriving UV from world/local position instead sidesteps the problem
 * entirely and gives a normal-looking, naturally tiled material regardless of how thin the original
 * source slice was.
 *
 * Quads are classified by their baked Direction relative to the block's FACING, not by sprite
 * identity: the picture (front) face and the edge/silhouette "step" quads both sample the SAME
 * "#front" sprite, so sprite identity alone can't tell them apart. Direction can: the picture face's
 * Direction always equals FACING, the back face's Direction always equals FACING.getOpposite(), and
 * every other quad (the edge extrusion) is perpendicular to both - true regardless of which of the 6
 * facings this particular baked variant represents, since FACING is read from the actual BlockState.
 */
public class PictureBakedModel extends BakedModelWrapper<BakedModel> {

    private static final int VERTEX_STRIDE = 8;
    private static final int X_OFFSET = 0;
    private static final int Y_OFFSET = 1;
    private static final int Z_OFFSET = 2;
    private static final int U_OFFSET = 4;
    private static final int V_OFFSET = 5;

    /**
     * Matches the tolerance vanilla itself uses for "is this coordinate touching a block boundary"
     * (see ModelBlockRenderer#calculateShape). Quads whose corners sit exactly on a whole-block
     * coordinate (e.g. the Hogwarts sign's oversized backing plate, whose corners are at exactly
     * -1.0/2.0 in X and 0.0/2.0 in Y) are exactly the case this guards: floating-point noise can push
     * one corner just below the integer and another just above it, so a naive floor() sends them to
     * different sides of the wrap and each corner ends up sampling a wildly different point on the
     * material sprite - interpolated across the quad, that shows up as a viewing-angle-dependent
     * flicker. Biasing the floor by this epsilon makes every corner that's "meant" to be at the same
     * integer land on the same side reliably.
     */
    private static final float BOUNDARY_EPSILON = 1.0e-4F;

    public PictureBakedModel(BakedModel originalModel) {
        super(originalModel);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                     ModelData extraData, @Nullable RenderType renderType) {
        if (state == null) {
            return super.getQuads(state, side, rand, extraData, renderType);
        }
        BlockState backMaterial = extraData.get(PictureMaterialProperty.BACK_MATERIAL);
        BlockState edgeMaterial = extraData.get(PictureMaterialProperty.EDGE_MATERIAL);
        // Boolean.TRUE.equals(...) rather than a plain unbox: ModelData.EMPTY.get(...) returns null
        // (not false) for an unset property, and PictureBlockEntityRenderer deliberately queries with
        // ModelData.EMPTY to fetch the front quad's geometry even while it's suppressed for normal
        // rendering - null must NOT suppress it there, only a real, synced "true" should.
        boolean suppressFront = Boolean.TRUE.equals(extraData.get(PictureImageProperty.HAS_IMAGE));
        if (backMaterial == null && edgeMaterial == null && !suppressFront) {
            return super.getQuads(state, side, rand, extraData, renderType);
        }

        List<BakedQuad> original = super.getQuads(state, side, rand, extraData, renderType);
        Direction facing = state.getValue(AbstractPictureBlock.FACING);
        Direction back = facing.getOpposite();

        List<BakedQuad> remapped = new ArrayList<>(original.size());
        for (BakedQuad quad : original) {
            Direction quadDirection = quad.getDirection();
            if (quadDirection == facing) {
                if (!suppressFront) {
                    remapped.add(quad);
                }
                continue;
            }
            if (suppressFront) {
                // A custom image is set: PictureBlockEntityRenderer draws back/edges itself, scaled to
                // match the picture's own (possibly multi-block) size - this block's own back/edge
                // quads would stay fixed at 1-block size regardless of the picture, and picture_blank's
                // own geometry there is additionally broken (see its class javadoc history). Suppress
                // both entirely rather than drawing a wrongly-sized or wrongly-shaped duplicate.
                continue;
            }
            boolean isEdge = quadDirection != back;
            BlockState targetMaterial = isEdge ? edgeMaterial : backMaterial;
            remapped.add(targetMaterial == null ? quad : remapQuad(quad, resolveMaterialSprite(targetMaterial)));
        }
        return remapped;
    }

    /** Package-private (not private): PictureBlockEntityRenderer also needs a material's sprite, for
     * the same reason - the atlas has no per-block API to look one up more directly than this. */
    static TextureAtlasSprite resolveMaterialSprite(BlockState material) {
        return Minecraft.getInstance().getBlockRenderer()
            .getBlockModel(material)
            .getParticleIcon(ModelData.EMPTY);
    }

    private static BakedQuad remapQuad(BakedQuad quad, TextureAtlasSprite newSprite) {
        int[] vertexData = Arrays.copyOf(quad.getVertices(), quad.getVertices().length);
        Direction.Axis axis = quad.getDirection().getAxis();

        float[] coordU = new float[4];
        float[] coordV = new float[4];
        for (int vertex = 0; vertex < 4; vertex++) {
            int base = vertex * VERTEX_STRIDE;
            float x = Float.intBitsToFloat(vertexData[base + X_OFFSET]);
            float y = Float.intBitsToFloat(vertexData[base + Y_OFFSET]);
            float z = Float.intBitsToFloat(vertexData[base + Z_OFFSET]);
            coordU[vertex] = switch (axis) {
                case X -> z;
                case Y -> x;
                case Z -> x;
            };
            coordV[vertex] = switch (axis) {
                case X -> y;
                case Y -> z;
                case Z -> y;
            };
        }

        // A quad spanning a whole block or more (e.g. the oversized sign's backing plate, or one of
        // the wider silhouette-outline bars) can't tile within itself - a single quad only has 4
        // corner UVs to interpolate between, nowhere near enough to represent a repeating pattern,
        // and the atlas doesn't support wrapping past a sprite's own bounds anyway. For those, stretch
        // the material's one sprite across the quad's own extent instead of tiling it. Smaller quads
        // (the common case - most of the silhouette extrusion) keep tiling by absolute position, which
        // is what makes them line up seamlessly with their neighbors.
        float minU = min4(coordU);
        float maxU = max4(coordU);
        float minV = min4(coordV);
        float maxV = max4(coordV);
        boolean stretchU = maxU - minU >= 1.0F;
        boolean stretchV = maxV - minV >= 1.0F;

        for (int vertex = 0; vertex < 4; vertex++) {
            int base = vertex * VERTEX_STRIDE;
            float fracU = stretchU
                ? (maxU > minU ? (coordU[vertex] - minU) / (maxU - minU) : 0.0F)
                : coordU[vertex] - Mth.floor(coordU[vertex] + BOUNDARY_EPSILON);
            float fracV = stretchV
                ? (maxV > minV ? (coordV[vertex] - minV) / (maxV - minV) : 0.0F)
                : coordV[vertex] - Mth.floor(coordV[vertex] + BOUNDARY_EPSILON);
            vertexData[base + U_OFFSET] = Float.floatToRawIntBits(newSprite.getU(fracU));
            vertexData[base + V_OFFSET] = Float.floatToRawIntBits(newSprite.getV(fracV));
        }
        return new BakedQuad(vertexData, quad.getTintIndex(), quad.getDirection(), newSprite, quad.isShade());
    }

    private static float min4(float[] v) {
        return Math.min(Math.min(v[0], v[1]), Math.min(v[2], v[3]));
    }

    private static float max4(float[] v) {
        return Math.max(Math.max(v[0], v[1]), Math.max(v[2], v[3]));
    }
}
