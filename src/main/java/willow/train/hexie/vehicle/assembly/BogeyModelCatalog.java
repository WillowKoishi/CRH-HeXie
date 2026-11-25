package willow.train.hexie.vehicle.assembly;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import willow.train.hexie.bridge.railway.TrackRegistryBridge;
import willow.train.hexie.bridge.vehicle.BogeyRegistryBridge;
import willow.train.hexie.bridge.world.WorldBlockBridge;
import willow.train.hexie.core.api.bogey.BogeyModel;
import willow.train.hexie.core.api.vehicle.Point3;
import willow.train.hexie.vehicle.bogey.core.BogeyStyleMigration;
import willow.train.hexie.vehicle.bogey.core.BuiltinBogeyModel;

/** Registration owned by HeXie. Custom providers supply bogey definitions without prescribing a data source. */
public final class BogeyModelCatalog implements BogeyRegistryBridge.BogeyModelCatalogView {
    /**
     * Pivot is measured from the bogey block centre in the carriage's north-forward frame.
     * A resolver for a differently oriented block must convert its pivot into that frame.
     * blockCenterHeight is the block centre above the route reference; production uses rail top.
     */

    private final Map<ResourceLocation, Function<CarriageSnapshot.Block, BogeyModel>> models = new ConcurrentHashMap<>();
    private static BogeyModelCatalog production;

    /**
     * Shared production catalog. Register custom providers here during common setup, after blocks
     * are registered. Every production assembly uses this same catalog; isolated callers may still
     * construct their own catalog and pass it to the assembly preparer.
     */
    public static synchronized BogeyModelCatalog production() {
        if (production == null) production = createProduction();
        return production;
    }

    private static BogeyModelCatalog createProduction() {
        var catalog = new BogeyModelCatalog();
        for (var block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof WorldBlockBridge.HexieBogeyBlockView bogey)) continue;
            var builtin = bogey.builtinModel();
            if (builtin == null) continue; // Custom styles need their own registered resolver.
            var registration = BuiltinBogeyModel.valueOf(builtin.name());
            var profile = registration.profile();
            var model = new BogeyModel(registration.id(),
                    profile.geometry(), profile.operating(), new Point3(0, 0, 0),
                    profile.mountType(), profile.lateralLimit(), 1.5 - TrackRegistryBridge.RAIL_SURFACE_HEIGHT);
            catalog.register(BuiltInRegistries.BLOCK.getKey(block), captured -> {
                var nbt = captured.entityData();
                if (nbt != null && nbt.contains("BogeyData")) {
                    if (!nbt.contains("BogeyData", Tag.TAG_COMPOUND))
                        throw new IllegalArgumentException("Invalid saved bogey data");
                    var saved = nbt.getCompound("BogeyData");
                    if (saved.contains("BogeyStyle") && (!saved.contains("BogeyStyle", Tag.TAG_STRING)
                            || !registration.style().equals(BogeyStyleMigration.normalizeStyle(
                                    ResourceLocation.tryParse(saved.getString("BogeyStyle")), registration.size()))))
                        throw new IllegalArgumentException("转向架样式不匹配："
                                + BuiltInRegistries.BLOCK.getKey(block) + "，位置 "
                                + nbt.getInt("x") + ", " + nbt.getInt("y") + ", " + nbt.getInt("z")
                                + "；保存值 " + saved.get("BogeyStyle")
                                + "；应为 " + registration.style());
                }
                return model;
            });
        }
        return catalog;
    }

    public void register(ResourceLocation block, BogeyModel model) {
        Objects.requireNonNull(model);
        register(block, ignored -> model);
    }

    /**
     * Source-independent definition lookup for custom bogeys. The captured block is context;
     * the provider decides where its BogeyModel/BogeyParameters come from (code, block state,
     * an external registry, saved data, etc.). No storage format is required.
     * Resolution happens at assembly, and the validated values are saved in the runtime snapshot.
     * Register once per custom block before assembly; duplicate built-in IDs are rejected.
     */
    public void register(ResourceLocation block, Function<CarriageSnapshot.Block, BogeyModel> resolver) {
        if (models.putIfAbsent(Objects.requireNonNull(block), Objects.requireNonNull(resolver)) != null)
            throw new IllegalArgumentException("Bogey block already registered: " + block);
    }

    public BogeyModel resolve(CarriageSnapshot.Block block) {
        var id = BuiltInRegistries.BLOCK.getKey(block.state().getBlock());
        var resolver = models.get(id);
        if (resolver == null) throw new IllegalArgumentException("No operating parameters registered for bogey: " + id);
        return Objects.requireNonNull(resolver.apply(block), "Bogey resolver returned no model");
    }
}
