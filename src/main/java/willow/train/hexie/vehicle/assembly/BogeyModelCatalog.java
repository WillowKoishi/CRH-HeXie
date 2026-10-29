package willow.train.hexie.vehicle.assembly;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.io.IOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import willow.train.hexie.blocks.bogeys.HexieBogeyBlock;
import willow.train.hexie.init.track.TrackMaterialInit;
import willow.train.hexie.vehicle.bogey.BogeyGeometry;
import willow.train.hexie.vehicle.bogey.HexieBogeyGeometries;
import willow.train.hexie.vehicle.runtime.BogeyParameters;
import willow.train.hexie.vehicle.runtime.BogeyMount;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint.Point;

/** Registration owned by HeXie. A custom bogey may resolve its model from its saved block data. */
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

    private final Map<ResourceLocation, Function<CarriageSnapshot.Block, Model>> models = new HashMap<>();

    /** Read once for an assembly. A data pack can replace these initial gameplay operating values. */
    public static BogeyModelCatalog production(ResourceManager resources) {
        var file = ResourceLocation.fromNamespaceAndPath("hexie", "vehicle/bogey_models.json");
        final JsonObject data;
        try (var reader = resources.getResourceOrThrow(file).openAsReader()) {
            data = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read bogey operating registrations: " + file, failure);
        }
        if (data.get("schema").getAsInt() != 1)
            throw new IllegalArgumentException("Unsupported bogey operating registration schema");
        var profiles = data.getAsJsonObject("models");
        var catalog = new BogeyModelCatalog();
        for (var block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof HexieBogeyBlock<?> bogey)) continue;
            var registration = identify(bogey);
            var profile = profiles.getAsJsonObject(registration.profile);
            if (profile == null || !profile.has("source") || !"gameplay_estimate".equals(profile.get("source").getAsString()))
                throw new IllegalArgumentException("Missing labelled operating values for " + registration.profile);
            var parameters = new BogeyParameters(number(profile, "load_kg"), number(profile, "traction_n"),
                    number(profile, "brake_n"), number(profile, "rolling_resistance_n"),
                    registration.geometry.wheelRadius(), number(profile, "drive_ratio"));
            var model = new Model(ResourceLocation.fromNamespaceAndPath("hexie", registration.model),
                    registration.geometry, parameters, new Point(0, 0, 0),
                    BogeyMount.Type.valueOf(profile.get("mount_type").getAsString().toUpperCase(java.util.Locale.ROOT)),
                    number(profile, "lateral_limit"), 1.5 - TrackMaterialInit.RAIL_SURFACE_HEIGHT);
            catalog.register(BuiltInRegistries.BLOCK.getKey(block), captured -> {
                var nbt = captured.entityData();
                if (nbt != null && nbt.contains("BogeyData")) {
                    if (!nbt.contains("BogeyData", Tag.TAG_COMPOUND))
                        throw new IllegalArgumentException("Invalid saved bogey data");
                    var saved = nbt.getCompound("BogeyData");
                    if (saved.contains("BogeyStyle") && (!saved.contains("BogeyStyle", Tag.TAG_STRING)
                            || !saved.getString("BogeyStyle").equals(bogey.getDefaultStyle().id.toString())))
                        throw new IllegalArgumentException("Saved bogey style does not match its registered block/model");
                }
                return model;
            });
        }
        return catalog;
    }

    private record Registration(String model, String profile, BogeyGeometry geometry) {}

    /** Use the registered style and size together to identify the concrete model. */
    private static Registration identify(HexieBogeyBlock<?> bogey) {
        String style = bogey.getDefaultStyle().id.toString();
        String size = bogey.getSize().id().toString();
        return switch (style + "/" + size) {
            case "hexie:hxstandardbogey/create:small" -> new Registration("cw2", "cw2", HexieBogeyGeometries.CW2);
            case "hexie:hxstandardbogey/hexie:pk209p" -> new Registration("pk209p", "pk209p", HexieBogeyGeometries.PK209P);
            case "hexie:hxstandardbogey/hexie:pk209p_no_motor" -> new Registration("pk209p_no_motor", "pk209p", HexieBogeyGeometries.PK209P);
            case "hexie:hxstandardbogey/hexie:pk209p_backward" -> new Registration("pk209p_backward", "pk209p", HexieBogeyGeometries.PK209P);
            case "hexie:hxstandardbogey/hexie:sw220k" -> new Registration("sw220k", "sw220k", HexieBogeyGeometries.SW220K);
            case "hexie:hxstandardbogey/hexie:sw220k_backward" -> new Registration("sw220k_backward", "sw220k", HexieBogeyGeometries.SW220K);
            case "hexie:hxstandardbogey/hexie:sw160" -> new Registration("sw160", "sw160", HexieBogeyGeometries.SW160);
            case "hexie:df11gbogey/create:small" -> new Registration("df11g", "df11g", HexieBogeyGeometries.DF11G);
            case "hexie:df11gbogey/hexie:backward" -> new Registration("df11g_backward", "df11g", HexieBogeyGeometries.DF11G);
            case "hexie:df11gbogey/hexie:qj" -> new Registration("qj", "qj", HexieBogeyGeometries.QJ);
            case "hexie:df11gbogey/hexie:qj_backward" -> new Registration("qj_backward", "qj", HexieBogeyGeometries.QJ);
            case "hexie:crhbogey/hexie:dt206" -> new Registration("dt206", "dt206", HexieBogeyGeometries.DT206);
            default -> throw new IllegalArgumentException("No operating model registered for bogey style/size " + style + "/" + size);
        };
    }

    private static double number(JsonObject object, String name) {
        if (!object.has(name) || !object.get(name).isJsonPrimitive() || !object.getAsJsonPrimitive(name).isNumber())
            throw new IllegalArgumentException("Missing numeric bogey parameter: " + name);
        double value = object.get(name).getAsDouble();
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite bogey parameter: " + name);
        return value;
    }

    public void register(ResourceLocation block, Model model) {
        Objects.requireNonNull(model);
        register(block, ignored -> model);
    }

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
