package com.copycatsign.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/**
 * Stores the (optional) chosen materials for a sign's back face and edges independently - see
 * PictureBakedModel for how they actually get rendered (re-textures the fixed silhouette geometry
 * using each material's own sprite via a UV remap, Create-Copycat-style, but without needing their
 * quad-geometry-cropping machinery since our shape never changes) - and the (optional) uploaded
 * custom picture shown on the front face (see ROADMAP.md Feature 3), rendered by a dedicated
 * BlockEntityRenderer (F3.3) rather than through the baked-model path, since it needs a DynamicTexture
 * outside the normal block atlas. Unlike the materials, the image doesn't need a ModelProperty for
 * this reason - a BlockEntityRenderer already gets this BlockEntity passed to it directly every frame.
 *
 * A null material slot means "no material chosen for that slot, use the block's own default texture"
 * (the plain masked-oak-planks look / the front-picture-sampled edge look the sign had before
 * Feature 1). A null imageHash means "no custom picture uploaded yet, show the placeholder".
 */
public class PictureBlockEntity extends BlockEntity {

    @Nullable
    private BlockState backMaterial;
    @Nullable
    private BlockState edgeMaterial;

    @Nullable
    private String imageHash;
    private int imageWidth;
    private int imageHeight;
    private float panX;
    private float panY;
    private float zoom = 1.0F;

    public PictureBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Nullable
    public BlockState getBackMaterial() {
        return backMaterial;
    }

    @Nullable
    public BlockState getEdgeMaterial() {
        return edgeMaterial;
    }

    public void setBackMaterial(@Nullable BlockState backMaterial) {
        this.backMaterial = backMaterial;
        syncToClient(true);
    }

    public void setEdgeMaterial(@Nullable BlockState edgeMaterial) {
        this.edgeMaterial = edgeMaterial;
        syncToClient(true);
    }

    @Nullable
    public String getImageHash() {
        return imageHash;
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public int getImageHeight() {
        return imageHeight;
    }

    /** Offset of the picture's center from the block's own center, in block units (-0.5..0.5 - the
     * center can be pushed up to the block's own edges, see PictureBlockEntityRenderer). */
    public float getPanX() {
        return panX;
    }

    public float getPanY() {
        return panY;
    }

    /** Multiplier on the picture's base size (imageWidth/height at 512px-per-block, see
     * PictureBlockEntityRenderer) - 1.0 is the default/neutral "100%" the editor's zoom slider starts
     * at, 2.0 and 0.5 its "+100%"/"-100%" ends. */
    public float getZoom() {
        return zoom;
    }

    /** Assigns a newly uploaded/selected picture, resetting pan/zoom back to "centered, 100% size". */
    public void setImage(@Nullable String imageHash, int imageWidth, int imageHeight) {
        this.imageHash = imageHash;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.panX = 0.0F;
        this.panY = 0.0F;
        this.zoom = 1.0F;
        syncToClient(true);
    }

    /** Live pan/zoom adjustment on the already-chosen picture (see the F3.4 editor screen). */
    public void setPanAndZoom(float panX, float panY, float zoom) {
        this.panX = Mth.clamp(panX, -0.5F, 0.5F);
        this.panY = Mth.clamp(panY, -0.5F, 0.5F);
        this.zoom = Mth.clamp(zoom, 0.5F, 2.0F);
        syncToClient(false);
    }

    private void syncToClient(boolean affectsModelData) {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            if (affectsModelData) {
                requestModelDataUpdate();
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (backMaterial != null) {
            tag.put("BackMaterial", NbtUtils.writeBlockState(backMaterial));
        }
        if (edgeMaterial != null) {
            tag.put("EdgeMaterial", NbtUtils.writeBlockState(edgeMaterial));
        }
        if (imageHash != null) {
            tag.putString("ImageHash", imageHash);
            tag.putInt("ImageWidth", imageWidth);
            tag.putInt("ImageHeight", imageHeight);
            tag.putFloat("PanX", panX);
            tag.putFloat("PanY", panY);
            tag.putFloat("Zoom", zoom);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        var blockLookup = registries.lookupOrThrow(Registries.BLOCK);
        backMaterial = tag.contains("BackMaterial")
            ? NbtUtils.readBlockState(blockLookup, tag.getCompound("BackMaterial"))
            : null;
        edgeMaterial = tag.contains("EdgeMaterial")
            ? NbtUtils.readBlockState(blockLookup, tag.getCompound("EdgeMaterial"))
            : null;
        imageHash = tag.contains("ImageHash") ? tag.getString("ImageHash") : null;
        imageWidth = tag.getInt("ImageWidth");
        imageHeight = tag.getInt("ImageHeight");
        panX = tag.contains("PanX") ? tag.getFloat("PanX") : 0.0F;
        panY = tag.contains("PanY") ? tag.getFloat("PanY") : 0.0F;
        zoom = tag.contains("Zoom") ? tag.getFloat("Zoom") : 1.0F;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * The default BlockEntity/IBlockEntityExtension implementation applies the incoming synced data
     * (via loadAdditional) but never tells the renderer to actually rebuild this chunk section - so
     * without this override, a material change stays invisible until something UNRELATED forces a
     * rebuild nearby (e.g. breaking a neighboring block). sendBlockUpdated schedules the chunk
     * rebuild; requestModelDataUpdate refreshes the ModelData the new PictureBakedModel reads from
     * (see IBlockEntityExtension#requestModelDataUpdate - it's a no-op unless called client-side,
     * which this is, since onDataPacket only ever runs on the client).
     */
    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        super.onDataPacket(connection, packet, registries);
        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
        requestModelDataUpdate();
    }

    /** Same reasoning as {@link #onDataPacket}, but for the tag a client gets when a chunk first
     * enters render distance (getUpdateTag/handleUpdateTag) rather than a live update packet. */
    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        requestModelDataUpdate();
    }

    @Override
    public ModelData getModelData() {
        return ModelData.builder()
            .with(PictureMaterialProperty.BACK_MATERIAL, backMaterial)
            .with(PictureMaterialProperty.EDGE_MATERIAL, edgeMaterial)
            .with(PictureImageProperty.HAS_IMAGE, imageHash != null)
            .build();
    }
}
