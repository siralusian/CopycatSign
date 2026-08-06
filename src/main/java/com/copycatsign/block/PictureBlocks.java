package com.copycatsign.block;

import com.copycatsign.CopycatSign;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry for the "flat picture" sign blocks (see AbstractPictureBlock). Every registered block is
 * a single physical cell (PictureBlockSingle) - a sign's model may render far larger than its own
 * cell (see its blockstate), so no special multi-cell registration is needed here. Push reaction is
 * left at the default (NORMAL, inherited from oak planks) on purpose: these are meant to be
 * mountable on Create contraptions (e.g. a train boiler), which would otherwise destroy anything
 * with PushReaction.DESTROY when the contraption assembles.
 *
 * One block per motif (2026-08-05 refactor) - thickness used to mean a separate registered block per
 * variant, but now that it's a plain blockstate property (see PictureThickness) there's no need for
 * that; the four thickness values just share this one block/item pair per motif.
 *
 * Ported from CobbleCompanion-Everything (2026-08-03/04 session) as the starting point for this
 * standalone mod - see ROADMAP.md for what's planned on top: in-game image upload, position/zoom.
 */
public final class PictureBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CopycatSign.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CopycatSign.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CopycatSign.MOD_ID);

    private static final List<DeferredItem<BlockItem>> CREATIVE_TAB_ITEMS = new ArrayList<>();

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> COPYCAT_SIGN_TAB =
        CREATIVE_TABS.register("copycat_sign", PictureBlocks::createCopycatSignTab);

    /**
     * The actual point of this mod (2026-08-05): a blank starting canvas the player uploads their
     * own picture onto (Feature 3, see ROADMAP.md), analogous to Immersive Paintings' Motive="none"
     * default. Hogwarts/5972 below were always just placeholder test content for the underlying
     * mechanics, not the intended end product - they stay registered, but this is the "real" item.
     * Placeholder appearance for now (before Feature 3's custom-image rendering exists): a plain
     * frame shape adapted from vanilla's models/block/template_item_frame.json, reusing vanilla's own
     * item_frame/oak_planks textures - visual only, none of the entity-based vanilla item frame's
     * actual behavior.
     */
    public static final DeferredBlock<PictureBlockSingle> PICTURE_BLANK = registerSignBlock("picture_blank");
    public static final DeferredItem<BlockItem> PICTURE_BLANK_ITEM = registerSignItem("picture_blank", PICTURE_BLANK);

    public static final DeferredBlock<PictureBlockSingle> HOGWARTS_EXPRESS_SCHILD =
        registerSignBlock("picture_hogwarts_express_schild");
    public static final DeferredItem<BlockItem> HOGWARTS_EXPRESS_SCHILD_ITEM =
        registerSignItem("picture_hogwarts_express_schild", HOGWARTS_EXPRESS_SCHILD);

    public static final DeferredBlock<PictureBlockSingle> PICTURE_5972 = registerSignBlock("picture_5972");
    public static final DeferredItem<BlockItem> PICTURE_5972_ITEM = registerSignItem("picture_5972", PICTURE_5972);

    private PictureBlocks() {
    }

    private static DeferredBlock<PictureBlockSingle> registerSignBlock(String id) {
        return BLOCKS.register(id, () -> new PictureBlockSingle(pictureProperties()));
    }

    private static DeferredItem<BlockItem> registerSignItem(String id, DeferredBlock<PictureBlockSingle> block) {
        DeferredItem<BlockItem> item = ITEMS.registerItem(id, props -> new PictureBlockItem(block.get(), props), new Item.Properties());
        CREATIVE_TAB_ITEMS.add(item);
        return item;
    }

    private static CreativeModeTab createCopycatSignTab() {
        return CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.copycatsign.copycat_sign"))
            .icon(() -> new ItemStack(PICTURE_BLANK_ITEM.get()))
            .displayItems((params, output) -> CREATIVE_TAB_ITEMS.forEach(item -> output.accept(item.get())))
            .build();
    }

    private static BlockBehaviour.Properties pictureProperties() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)
            .noOcclusion()
            .strength(1.5F);
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
    }
}
