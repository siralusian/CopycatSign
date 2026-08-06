package com.copycatsign.client;

import com.copycatsign.block.AbstractPictureBlock;
import com.copycatsign.block.PictureBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Draws a sign's uploaded custom picture (see PictureBlockEntity/ROADMAP.md Feature 3) - front, back
 * and edges - outside the normal baked-quad path (PictureBakedModel suppresses all three of its own
 * quads whenever a custom image is set, see its javadoc). A DynamicTexture isn't part of the block
 * atlas, so the front face can't be sampled the way PictureBakedModel remaps material sprites; back
 * and edges COULD still go through that path, but they need to be sized to match the picture (see
 * below), which PictureBakedModel's borrowed-from-PICTURE_5972 quads never are - so all three are
 * built procedurally here instead, together, so they're consistently sized.
 *
 * The picture's size is NOT fixed to the block's own 1x1 cell: by convention (settled 2026-08-06,
 * matching the reference 5972 sign's own proportions) 512 image pixels = 1 block at 100%/"0%" zoom. A
 * wide or tall image extends past the placed block into whichever neighbors it visually overlaps,
 * exactly like the hand-authored oversized Hogwarts sign model already does - except here the size
 * comes from the actual uploaded image's dimensions rather than being baked into a model ahead of
 * time. The back face and edges match this same size (unlike PICTURE_5972's own fixed 1-block back/
 * edge geometry), so a big picture doesn't end up with a comically small backing panel behind it.
 *
 * Edges are always silhouette-shaped (see PictureSilhouette's javadoc for the vanilla ItemModelGenerator
 * port this uses) - the same "beveled edge" look the Hogwarts/5972 signs' own hand-baked models
 * already have, just computed at runtime instead of offline. If no edge material is chosen, each span
 * samples the matching strip of the source image; if one is, it tiles that material's sprite instead
 * (world-position UV, mirroring PictureBakedModel's remapQuad) - either way the SHAPE stays the
 * silhouette, only the texture source changes. The back face has no silhouette shape of its own (no
 * alpha data would make sense for an arbitrary chosen material) - it's a plain flat panel matching the
 * front's overall size, tiling the chosen material or, if none is chosen, a plain oak-plank default
 * (PICTURE_5972's own unmapped default doesn't work here - its "_backing" texture is masked to ITS OWN
 * artwork's silhouette, not this sign's arbitrary uploaded one).
 *
 * Only the block's own physical 1x1 cell has collision/an outline (see AbstractPictureBlock) - the
 * overhang here is purely visual, the same tradeoff the Hogwarts sign's model already makes.
 */
public class PictureBlockEntityRenderer implements BlockEntityRenderer<PictureBlockEntity> {

    /** How many pixels of an uploaded image correspond to one block's width/height at 100% zoom. */
    private static final float PIXELS_PER_BLOCK = 512.0F;

    private static final BlockState DEFAULT_BACK_MATERIAL = Blocks.OAK_PLANKS.defaultBlockState();

    /**
     * Per PictureSilhouette.Facing ordinal (UP, DOWN, LEFT, RIGHT), the 4 vertices of that span's edge
     * quad - each {rightSlot, upSlot, depthSlot, uSlot, vSlot} with 0=min/back/near, 1=max/front/far.
     * Transcribed from vanilla's FaceBakery (FaceInfo's per-direction vertex table crossed with
     * BlockFaceUV's vertex->corner mapping) - see the 2026-08-06 session notes for the full derivation;
     * this is the exact same table vanilla itself uses to bake ItemModelGenerator's span elements, just
     * evaluated here directly instead of via BlockElement/FaceBakery (which need a live TextureAtlasSprite
     * we don't have for a runtime DynamicTexture).
     */
    private static final int[][][] SPAN_VERTICES = {
        {{0, 1, 0, 0, 0}, {0, 1, 1, 0, 1}, {1, 1, 1, 1, 1}, {1, 1, 0, 1, 0}}, // UP
        {{0, 0, 1, 0, 0}, {0, 0, 0, 0, 1}, {1, 0, 0, 1, 1}, {1, 0, 1, 1, 0}}, // DOWN
        {{1, 1, 1, 0, 0}, {1, 0, 1, 0, 1}, {1, 0, 0, 1, 1}, {1, 1, 0, 1, 0}}, // LEFT (canonical EAST)
        {{0, 1, 0, 0, 0}, {0, 0, 0, 0, 1}, {0, 0, 1, 1, 1}, {0, 1, 1, 1, 0}}, // RIGHT (canonical WEST)
    };

    public PictureBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(PictureBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                        MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        String hash = blockEntity.getImageHash();
        int imageWidth = blockEntity.getImageWidth();
        int imageHeight = blockEntity.getImageHeight();
        if (hash == null || blockEntity.getLevel() == null || imageWidth <= 0 || imageHeight <= 0) {
            return;
        }
        ResourceLocation textureId = ClientImageManager.resolveTexture(hash);
        if (textureId == null) {
            return;
        }

        BlockState state = blockEntity.getBlockState();
        Direction facing = state.getValue(AbstractPictureBlock.FACING);
        Direction upHint = state.getValue(AbstractPictureBlock.UP_HINT);
        Direction localUp = AbstractPictureBlock.resolveLocalUp(facing, upHint);
        Direction localRight = AbstractPictureBlock.resolveLocalRight(facing, localUp);

        // The block's own physical box (see AbstractPictureBlock#computeShape) always spans the FULL
        // 0..1 range on both non-facing axes - only the facing axis is constrained by position/
        // thickness - so the picture's un-panned center sits at 0.5 on those two axes, and the sign's
        // actual thickness is exactly this box's extent on the facing axis (frontDepth to backDepth).
        AABB box = AbstractPictureBlock.computeShape(facing,
            state.getValue(AbstractPictureBlock.POSITION), state.getValue(AbstractPictureBlock.THICKNESS)).bounds();
        boolean positive = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        float frontDepth = (float) switch (facing.getAxis()) {
            case X -> positive ? box.maxX : box.minX;
            case Y -> positive ? box.maxY : box.minY;
            case Z -> positive ? box.maxZ : box.minZ;
        };
        float backDepth = (float) switch (facing.getAxis()) {
            case X -> positive ? box.minX : box.maxX;
            case Y -> positive ? box.minY : box.maxY;
            case Z -> positive ? box.minZ : box.maxZ;
        };

        float zoom = blockEntity.getZoom();
        float widthBlocks = (imageWidth / PIXELS_PER_BLOCK) * zoom;
        float heightBlocks = (imageHeight / PIXELS_PER_BLOCK) * zoom;
        float panRight = blockEntity.getPanX();
        float panUp = blockEntity.getPanY();

        PoseStack.Pose pose = poseStack.last();
        // Culled, not entityCutoutNoCull: the picture must NOT show through from behind (that's the
        // separate, opaque back face's job, drawn below). Confirmed correct front-facing winding by
        // construction: "right" is defined (see AbstractPictureBlock#resolveLocalRight) as what appears
        // to someone standing in FRONT looking at the sign, and the bottom-left/bottom-right/top-right/
        // top-left vertex order below traces a counter-clockwise loop in THAT viewer's screen space -
        // the standard front-facing winding OpenGL/Minecraft expects.
        //
        // bufferSource.getBuffer(...) is called fresh right before each stage below rather than cached
        // across stages: MultiBufferSource.BufferSource only keeps ONE non-fixed ("shared") builder
        // live at a time - requesting a different RenderType ends whichever shared builder was active,
        // so a consumer obtained earlier for a different type is a dangling reference by the time a
        // later stage tries to use it ("IllegalStateException: Not building!" on addVertex).
        emitFrontQuad(bufferSource.getBuffer(RenderType.entityCutout(textureId)), pose, packedLight, packedOverlay,
            facing, localRight, localUp, frontDepth, widthBlocks, heightBlocks, panRight, panUp);

        BlockState backMaterial = blockEntity.getBackMaterial();
        String backMaterialKey = backMaterial != null
            ? BuiltInRegistries.BLOCK.getKey(backMaterial.getBlock()).toString()
            : "default";
        TextureAtlasSprite backSprite = PictureBakedModel.resolveMaterialSprite(
            backMaterial != null ? backMaterial : DEFAULT_BACK_MATERIAL);
        ResourceLocation backingTextureId = ClientImageManager.resolveBackingTexture(hash, backMaterialKey, backSprite);
        if (backingTextureId != null) {
            emitBackQuad(bufferSource.getBuffer(RenderType.entityCutout(backingTextureId)), pose, packedLight, packedOverlay,
                facing, localRight, localUp, backDepth, widthBlocks, heightBlocks, panRight, panUp);
        }

        BlockState edgeMaterial = blockEntity.getEdgeMaterial();
        List<PictureSilhouette.Span> spans = ClientImageManager.resolveSilhouette(hash);
        if (!spans.isEmpty()) {
            // A material swatch (see ClientImageManager#resolveMaterialSwatch) rather than sampling the
            // material straight from the block atlas: these span quads are often far thinner than a
            // texel, and the atlas's mipmapping blurs the material badly at that scale - a fresh,
            // non-mipmapped copy (the same trick already used for the back face) stays crisp.
            boolean useMaterial = edgeMaterial != null;
            ResourceLocation edgeTextureId = textureId;
            if (useMaterial) {
                String edgeMaterialKey = BuiltInRegistries.BLOCK.getKey(edgeMaterial.getBlock()).toString();
                TextureAtlasSprite edgeSprite = PictureBakedModel.resolveMaterialSprite(edgeMaterial);
                edgeTextureId = ClientImageManager.resolveMaterialSwatch(edgeMaterialKey, edgeSprite);
            }
            if (edgeTextureId != null) {
                VertexConsumer spanConsumer = bufferSource.getBuffer(RenderType.entityCutout(edgeTextureId));
                for (PictureSilhouette.Span span : spans) {
                    emitSpanQuad(spanConsumer, pose, packedLight, packedOverlay, facing, localRight, localUp,
                        backDepth, frontDepth, widthBlocks, heightBlocks, panRight, panUp, imageWidth, imageHeight, span, useMaterial);
                }
            }
        }
    }

    private static void emitFrontQuad(VertexConsumer consumer, PoseStack.Pose pose, int packedLight, int packedOverlay,
                                       Direction facing, Direction localRight, Direction localUp, float frontDepth,
                                       float widthBlocks, float heightBlocks, float panRight, float panUp) {
        float normalX = facing.getStepX();
        float normalY = facing.getStepY();
        float normalZ = facing.getStepZ();

        // bottom-left, bottom-right, top-right, top-left (as the picture's own viewer would see it).
        float[] rightFrac = {-0.5F, 0.5F, 0.5F, -0.5F};
        float[] upFrac = {-0.5F, -0.5F, 0.5F, 0.5F};
        float[] u = {0, 1, 1, 0};
        float[] v = {1, 1, 0, 0};

        for (int i = 0; i < 4; i++) {
            float[] point = worldPoint(facing, localRight, localUp, rightFrac[i] * widthBlocks + panRight,
                upFrac[i] * heightBlocks + panUp, frontDepth);
            consumer.addVertex(pose, point[0], point[1], point[2])
                .setColor(255, 255, 255, 255)
                .setUv(u[i], v[i])
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(pose, normalX, normalY, normalZ);
        }
    }

    private static void emitBackQuad(VertexConsumer consumer, PoseStack.Pose pose, int packedLight, int packedOverlay,
                                      Direction facing, Direction localRight, Direction localUp, float backDepth,
                                      float widthBlocks, float heightBlocks, float panRight, float panUp) {
        Direction back = facing.getOpposite();
        float normalX = back.getStepX();
        float normalY = back.getStepY();
        float normalZ = back.getStepZ();

        // Mirrored order compared to the front quad (bottom-RIGHT first, not bottom-left): someone
        // viewing the back stands on the opposite side and so sees "right" flipped relative to the
        // front viewer - tracing the same mirrored loop keeps this counter-clockwise (front-facing) for
        // THAT viewer, so it's visible from behind and culled from the front, matching a real back panel.
        // UV is NOT separately mirrored to compensate - the backing texture (see ClientImageManager/
        // PictureBackingTexture) shares the picture's own pixel layout, sampled at the SAME world
        // position each pixel would sit at on the front; viewed from behind, that position is already
        // mirrored relative to a front viewer purely from standing on the opposite side, exactly like a
        // real translucent cutout would look - matching UV to world position (not to screen handedness)
        // reproduces that automatically.
        float[] rightFrac = {0.5F, -0.5F, -0.5F, 0.5F};
        float[] upFrac = {-0.5F, -0.5F, 0.5F, 0.5F};
        float[] u = {1, 0, 0, 1};
        float[] v = {1, 1, 0, 0};

        for (int i = 0; i < 4; i++) {
            float[] point = worldPoint(facing, localRight, localUp, rightFrac[i] * widthBlocks + panRight,
                upFrac[i] * heightBlocks + panUp, backDepth);
            consumer.addVertex(pose, point[0], point[1], point[2])
                .setColor(255, 255, 255, 255)
                .setUv(u[i], v[i])
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(pose, normalX, normalY, normalZ);
        }
    }

    /** useMaterial false means "sample the matching strip of the uploaded image instead" (the default,
     * no-material look, consumer bound to the picture's own texture) - true tiles the edge material's
     * swatch by world position instead (consumer bound to that swatch - see the caller). */
    private static void emitSpanQuad(VertexConsumer consumer, PoseStack.Pose pose, int packedLight, int packedOverlay,
                                      Direction facing, Direction localRight, Direction localUp,
                                      float backDepth, float frontDepth, float widthBlocks, float heightBlocks,
                                      float panRight, float panUp, int imageWidth, int imageHeight,
                                      PictureSilhouette.Span span, boolean useMaterial) {
        float posRightMin;
        float posRightMax;
        float posUpMinRaw;
        float posUpMaxRaw;
        float uMin;
        float uMax;
        float vMin;
        float vMax;
        switch (span.facing) {
            case UP -> {
                posRightMin = span.min / (float) imageWidth;
                posRightMax = (span.max + 1) / (float) imageWidth;
                posUpMinRaw = posUpMaxRaw = span.anchor / (float) imageHeight;
                uMin = posRightMin;
                uMax = posRightMax;
                vMin = span.anchor / (float) imageHeight;
                vMax = (span.anchor + 1) / (float) imageHeight;
            }
            case DOWN -> {
                posRightMin = span.min / (float) imageWidth;
                posRightMax = (span.max + 1) / (float) imageWidth;
                posUpMinRaw = posUpMaxRaw = (span.anchor + 1) / (float) imageHeight;
                uMin = posRightMin;
                uMax = posRightMax;
                vMin = span.anchor / (float) imageHeight;
                vMax = (span.anchor + 1) / (float) imageHeight;
            }
            case LEFT -> {
                posRightMin = posRightMax = span.anchor / (float) imageWidth;
                posUpMinRaw = span.min / (float) imageHeight;
                posUpMaxRaw = (span.max + 1) / (float) imageHeight;
                uMin = span.anchor / (float) imageWidth;
                uMax = (span.anchor + 1) / (float) imageWidth;
                vMin = (span.max + 1) / (float) imageHeight;
                vMax = span.min / (float) imageHeight;
            }
            default -> { // RIGHT
                posRightMin = posRightMax = (span.anchor + 1) / (float) imageWidth;
                posUpMinRaw = span.min / (float) imageHeight;
                posUpMaxRaw = (span.max + 1) / (float) imageHeight;
                uMin = span.anchor / (float) imageWidth;
                uMax = (span.anchor + 1) / (float) imageWidth;
                vMin = (span.max + 1) / (float) imageHeight;
                vMax = span.min / (float) imageHeight;
            }
        }
        float posUpMin = 1.0F - posUpMinRaw;
        float posUpMax = 1.0F - posUpMaxRaw;

        Direction canonical = span.facing.canonicalDirection();
        Direction normalDirection = switch (canonical) {
            case UP -> localUp;
            case DOWN -> localUp.getOpposite();
            case EAST -> localRight;
            case WEST -> localRight.getOpposite();
            default -> throw new IllegalStateException("unexpected span direction " + canonical);
        };
        float normalX = normalDirection.getStepX();
        float normalY = normalDirection.getStepY();
        float normalZ = normalDirection.getStepZ();

        // Whichever of right/up is this span's VARYING axis (see the switch above - the other one is
        // degenerate, fixed at the anchor) carries the "transverse" slot for material tiling below.
        boolean rightVaries = span.facing == PictureSilhouette.Facing.UP || span.facing == PictureSilhouette.Facing.DOWN;
        // How deep (in the 16-pixel-per-block convention every material sprite already uses) this
        // sign's actual thickness is - sampling only THIS MUCH of the sprite's V range for the depth
        // axis (not the full 0..1) keeps the material's own proportions correct regardless of which
        // thickness the player picked, instead of always squashing/stretching the whole sprite height
        // into whatever the current thickness happens to be.
        float depthBlocks = Math.abs(frontDepth - backDepth);

        int[][] vertices = SPAN_VERTICES[span.facing.ordinal()];
        for (int[] vertex : vertices) {
            float fracRight = (vertex[0] == 0 ? posRightMin : posRightMax) - 0.5F;
            float fracUp = (vertex[1] == 0 ? posUpMin : posUpMax) - 0.5F;
            float depth = vertex[2] == 0 ? backDepth : frontDepth;
            float rightOffset = fracRight * widthBlocks + panRight;
            float upOffset = fracUp * heightBlocks + panUp;

            float u;
            float v;
            if (useMaterial) {
                // Tile the transverse axis by absolute world position (like a normal wall built from
                // this material would) rather than stretching the whole sprite across each individual
                // span: adjacent spans then each show the correspondingly-adjacent slice of the SAME
                // continuous tiled pattern, reconstructing a coherent material surface instead of a
                // patchwork of independently-squashed copies. The depth axis isn't tiled (thickness
                // never reaches a full block) - it's a plain proportional slice, see depthBlocks above.
                // u/v go straight to the swatch's own [0,1] space (see ClientImageManager#
                // resolveMaterialSwatch) - no atlas UV conversion needed, unlike a normal material
                // sprite, since the swatch is its own standalone texture, not stitched into the atlas.
                float transverseOffset = rightVaries ? rightOffset : upOffset;
                u = transverseOffset - Mth.floor(transverseOffset);
                v = vertex[2] == 0 ? 0.0F : depthBlocks;
            } else {
                u = vertex[3] == 0 ? uMin : uMax;
                v = vertex[4] == 0 ? vMin : vMax;
            }

            float[] point = worldPoint(facing, localRight, localUp, rightOffset, upOffset, depth);
            consumer.addVertex(pose, point[0], point[1], point[2])
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(pose, normalX, normalY, normalZ);
        }
    }

    private static float[] worldPoint(Direction facing, Direction localRight, Direction localUp,
                                       float right, float up, float depth) {
        float x = axisBase(facing, Direction.Axis.X, depth) + right * localRight.getStepX() + up * localUp.getStepX();
        float y = axisBase(facing, Direction.Axis.Y, depth) + right * localRight.getStepY() + up * localUp.getStepY();
        float z = axisBase(facing, Direction.Axis.Z, depth) + right * localRight.getStepZ() + up * localUp.getStepZ();
        return new float[]{x, y, z};
    }

    private static float axisBase(Direction facing, Direction.Axis axis, float depth) {
        return facing.getAxis() == axis ? depth : 0.5F;
    }
}
