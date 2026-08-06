package com.copycatsign;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side rules for which blocks players may apply as a sign's back/edge material. */
public final class CopycatSignConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue ALLOW_BLOCK_ENTITY_MATERIALS = BUILDER
        .comment(
            "Whether blocks that normally have their own BlockEntity (chests, furnaces, etc.) may be",
            "used as a sign's material. Such blocks lose their BlockEntity behavior entirely when used",
            "this way (only their appearance is copied) - matches Create Copycat's equivalent option.",
            "Individual blocks can still be allowed via the copycatsign:material_block_entity_whitelist",
            "block tag regardless of this setting."
        )
        .define("allowBlockEntityMaterials", false);

    private static final ModConfigSpec.IntValue MAX_IMAGE_DIMENSION = BUILDER
        .comment(
            "Maximum width/height (in pixels) accepted for an uploaded custom picture. Uploads",
            "exceeding this in either dimension are rejected outright rather than silently resized."
        )
        .defineInRange("maxImageDimension", 1024, 16, 4096);

    private static final ModConfigSpec.IntValue MAX_IMAGE_BYTES = BUILDER
        .comment("Maximum size (in bytes) of an uploaded custom picture's encoded PNG data.")
        .defineInRange("maxImageBytes", 4 * 1024 * 1024, 1024, 64 * 1024 * 1024);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private CopycatSignConfig() {
    }

    public static boolean allowBlockEntityMaterials() {
        return ALLOW_BLOCK_ENTITY_MATERIALS.get();
    }

    public static int maxImageDimension() {
        return MAX_IMAGE_DIMENSION.get();
    }

    public static int maxImageBytes() {
        return MAX_IMAGE_BYTES.get();
    }
}
