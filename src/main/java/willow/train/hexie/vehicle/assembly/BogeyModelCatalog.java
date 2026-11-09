package willow.train.hexie.vehicle.assembly;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import willow.train.hexie.blocks.bogeys.HexieBogeyBlock;
import willow.train.hexie.bogey.BogeyStyleMigration;
import willow.train.hexie.init.track.TrackMaterialInit;
import willow.train.hexie.vehicle.bogey.BogeyGeometry;
import willow.train.hexie.vehicle.bogey.BuiltinBogeyModel;
import willow.train.hexie.vehicle.runtime.BogeyParameters;
import willow.train.hexie.vehicle.runtime.BogeyMount;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint.Point;

/** Registration owned by HeXie. Custom providers supply bogey definitions without prescribing a data source. */
public final class BogeyModelCatalog {
    /**
     * Pivot is measured from the bogey block centre in the carriage's north-forward frame.
     * A resolver for a differently oriented block must convert its pivot into that frame.
     * blockCenterHeight is the block centre above the route reference; production uses rail top.
     */
    public record Model(ResourceLocation id, BogeyGeometry geometry, BogeyParameters operating,
                        Point pivot, BogeyMount.Type type, double lateralLimit, double blockCenterHeight) {
        public Model(ResourceLocation id, BogeyGeometry geometry, BogeyParameters operating) {
            this(id, geometry, operating, new Point(0, 0, 0));
        }

        /** Legacy direct attachment mode; production registrations should specify the model's type. */
        public Model(ResourceLocation id, BogeyGeometry geometry, BogeyParameters operating, Point pivot) {
            this(id, geometry, operating, pivot, BogeyMount.Type.BOLSTERED, 0);
        }

        /** Existing callers whose track reference already passes through the block centre. */
        public Model(ResourceLocation id, BogeyGeometry geometry, BogeyParameters operating,
                     Point pivot, BogeyMount.Type type, double lateralLimit) {
            this(id, geometry, operating, pivot, type, lateralLimit, 0);
        }

        public Model {
            Objects.requireNonNull(id);
            Objects.requireNonNull(geometry);
            Objects.requireNonNull(operating);
            Objects.requireNonNull(pivot, "pivot");
            Objects.requireNonNull(type, "type");
            if (!Double.isFinite(lateralLimit) || lateralLimit < 0)
                throw new IllegalArgumentException("Lateral limit must be finite and non-negative");
            if (!Double.isFinite(blockCenterHeight))
                throw new IllegalArgumentException("Block centre height must be finite");
            if (Math.abs(geometry.wheelRadius() - operating.wheelRadiusM()) > 1e-9)
                throw new IllegalArgumentException("Geometry and operating wheel radii disagree");
        }
    }

    private final Map<ResourceLocation, Function<CarriageSnapshot.Block, Model>> models = new ConcurrentHashMap<>();
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
            if (!(block instanceof HexieBogeyBlock<?> bogey)) continue;
            var registration = BuiltinBogeyModel.find(bogey.getDefaultStyle().id, bogey.getSize().id());
            if (registration == null) continue; // Custom styles need their own registered resolver.
            var profile = registration.profile();
            var model = new Model(registration.id(),
                    profile.geometry(), profile.operating(), new Point(0, 0, 0),
                    profile.mountType(), profile.lateralLimit(), 1.5 - TrackMaterialInit.RAIL_SURFACE_HEIGHT);
            catalog.register(BuiltInRegistries.BLOCK.getKey(block), captured -> {
                var nbt = captured.entityData();
                if (nbt != null && nbt.contains("BogeyData")) {
                    if (!nbt.contains("BogeyData", Tag.TAG_COMPOUND))
                        throw new IllegalArgumentException("Invalid saved bogey data");
                    var saved = nbt.getCompound("BogeyData");
                    if (saved.contains("BogeyStyle") && (!saved.contains("BogeyStyle", Tag.TAG_STRING)
                            || !bogey.getDefaultStyle().id.equals(BogeyStyleMigration.normalizeStyle(
                                    ResourceLocation.tryParse(saved.getString("BogeyStyle")), bogey.getSize().id()))))
                        throw new IllegalArgumentException("转向架样式不匹配："
                                + BuiltInRegistries.BLOCK.getKey(block) + "，位置 "
                                + nbt.getInt("x") + ", " + nbt.getInt("y") + ", " + nbt.getInt("z")
                                + "；保存值 " + saved.get("BogeyStyle")
                                + "；应为 " + bogey.getDefaultStyle().id);
                }
                return model;
            });
        }
        return catalog;
    }

    public void register(ResourceLocation block, Model model) {
        Objects.requireNonNull(model);
        register(block, ignored -> model);
    }

    /**
     * Source-independent definition lookup for custom bogeys. The captured block is context;
     * the provider decides where its Model/BogeyParameters come from (code, block state,
     * an external registry, saved data, etc.). No storage format is required.
     * Resolution happens at assembly, and the validated values are saved in the runtime snapshot.
     * Register once per custom block before assembly; duplicate built-in IDs are rejected.
     */
    public void register(ResourceLocation block, Function<CarriageSnapshot.Block, Model> resolver) {
        if (models.putIfAbsent(Objects.requireNonNull(block), Objects.requireNonNull(resolver)) != null)
            throw new IllegalArgumentException("Bogey block already registered: " + block);
    }

    public Model resolve(CarriageSnapshot.Block block) {
        var id = BuiltInRegistries.BLOCK.getKey(block.state().getBlock());
        var resolver = models.get(id);
        if (resolver == null) throw new IllegalArgumentException("No operating parameters registered for bogey: " + id);
        return Objects.requireNonNull(resolver.apply(block), "Bogey resolver returned no model");
    }
}
