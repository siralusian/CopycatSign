package com.copycatsign.block;

import com.copycatsign.CopycatSign;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class PictureBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CopycatSign.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PictureBlockEntity>> PICTURE =
        BLOCK_ENTITY_TYPES.register("picture", PictureBlockEntities::createPictureType);

    private PictureBlockEntities() {
    }

    private static BlockEntityType<PictureBlockEntity> createPictureType() {
        return BlockEntityType.Builder.of(
            (pos, state) -> new PictureBlockEntity(PICTURE.get(), pos, state),
            PictureBlocks.PICTURE_BLANK.get(),
            PictureBlocks.HOGWARTS_EXPRESS_SCHILD.get(),
            PictureBlocks.PICTURE_5972.get()
        ).build(null);
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
