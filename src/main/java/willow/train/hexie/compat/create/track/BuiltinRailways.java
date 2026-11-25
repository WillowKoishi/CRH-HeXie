package willow.train.hexie.compat.create.track;

import net.minecraft.resources.ResourceLocation;
import willow.train.hexie.core.api.railway.RailwayDefinition;

/** Built-in declarations use the same public schema as add-ons; legacy block IDs are retained for save migration. */
public final class BuiltinRailways {
    public static final String STANDARD_BLOCK_PATH = "create_standard_track";
    public static final String BALLASTLESS_BLOCK_PATH = "create_ballastless_track";
    public static final String TIELESS_BLOCK_PATH = "create_tieless_track";

    public static final ResourceLocation STANDARD_ID = id("create_standard");
    public static final ResourceLocation BALLASTLESS_ID = id("create_ballastless");
    public static final ResourceLocation TIELESS_ID = id("create_tieless");
    public static final ResourceLocation LEGACY_STANDARD_BLOCK_ID = id("standard_track");
    public static final ResourceLocation LEGACY_BALLASTLESS_BLOCK_ID = id("ballastless_track");
    public static final ResourceLocation LEGACY_TIELESS_BLOCK_ID = id("tieless_track");

    public static final RailwayDefinition STANDARD = RailwayDefinition.standard(
            id(STANDARD_BLOCK_PATH), "Create Standard", RailwayDefinition.Bed.BALLAST);
    public static final RailwayDefinition BALLASTLESS = RailwayDefinition.standard(
            id(BALLASTLESS_BLOCK_PATH), "Create Ballastless", RailwayDefinition.Bed.SLAB);
    public static final RailwayDefinition TIELESS = RailwayDefinition.standard(
            id(TIELESS_BLOCK_PATH), "Create Tieless", RailwayDefinition.Bed.NONE);

    private BuiltinRailways() {}
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("hexie", path); }
}
