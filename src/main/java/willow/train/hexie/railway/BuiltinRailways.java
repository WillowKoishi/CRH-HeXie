package willow.train.hexie.railway;

import net.minecraft.resources.ResourceLocation;
import willow.train.hexie.api.railway.RailwayDefinition;

/** Built-in declarations use the same public schema as add-ons; IDs are save-compatible. */
public final class BuiltinRailways {
    public static final ResourceLocation STANDARD_ID = id("standard");
    public static final ResourceLocation BALLASTLESS_ID = id("ballastless");
    public static final ResourceLocation TIELESS_ID = id("tieless");
    public static final RailwayDefinition STANDARD = RailwayDefinition.standard(id("standard_track"), "Standard", RailwayDefinition.Bed.BALLAST);
    public static final RailwayDefinition BALLASTLESS = RailwayDefinition.standard(id("ballastless_track"), "Ballastless", RailwayDefinition.Bed.SLAB);
    public static final RailwayDefinition TIELESS = RailwayDefinition.standard(id("tieless_track"), "Tieless", RailwayDefinition.Bed.NONE);
    private BuiltinRailways() {}
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("hexie", path); }
}
