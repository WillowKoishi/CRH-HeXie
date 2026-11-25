package willow.train.hexie.render.display;

// split from willow/train/hexie/client/render/display/PassengerPanelModels.java

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2PartBlockView;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.render.carriage.CarriageTransparencyCompat;

import static willow.train.hexie.render.display.PassengerPanelExterior.exterior;
import static willow.train.hexie.render.display.PassengerPanelExterior.onExterior;
import static willow.train.hexie.render.display.PassengerPanelQuads.cut;
import static willow.train.hexie.render.display.PassengerPanelQuads.normal;
import static willow.train.hexie.render.display.PassengerPanelQuads.transform;

/** State-only shell apertures shared by terrain, snapshots and carriage meshes. */
public final class PassengerPanelModels {
    /** Display-plane centers in final block coordinates; an absent screen has a null origin. */
    public record Layout(Vec3 signOrigin, Vec3 numberOrigin, Vec3 right, Vec3 up, Vec3 normal) {}

    record Face(BakedQuad quad, RenderType layer) {}
    record Opening(Vec3 center, double width, double height, String model) {}
    private static final String SIGN = "crh2a_electronic_signage";
    private static final String NUMBER = "crh2a_carriage_number_display";
    private static final double DISPLAY_DEPTH = -.36 / 16;
    public static final double EPS = 1e-6;
    private static volatile Map<String, BakedModel> screenModels = Map.of();
    private static volatile Map<BlockState, Layout> layouts = new ConcurrentHashMap<>();
    private static volatile Map<BlockState, List<BakedQuad>> displayGlass = new ConcurrentHashMap<>();

    private PassengerPanelModels() {}

    public static void register() {
        HexieBridges.modBootstrap().modBus().addListener(PassengerPanelModels::additional);
        // Prepare the partial models before CRH2StaticSections captures its immutable meshes.
        HexieBridges.modBootstrap().modBus().addListener(EventPriority.HIGH, PassengerPanelModels::bake);
    }

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(
                HexieConstants.MOD_ID, "block/crh2a/displays/" + name));
    }

    private static void additional(ModelEvent.RegisterAdditional event) {
        for (String name : List.of(SIGN, NUMBER))
            for (String layer : List.of("body", "glass")) event.register(model(name + "_" + layer));
    }

    private static void bake(ModelEvent.ModifyBakingResult event) {
        var parts = new HashMap<String, BakedModel>();
        for (String name : List.of(SIGN, NUMBER)) for (String layer : List.of("body", "glass")) {
            var key = name + "_" + layer;
            var baked = event.getModels().get(model(key));
            if (baked == null) throw new IllegalStateException("Missing passenger display model: " + key);
            parts.put(key, baked);
        }
        screenModels = Map.copyOf(parts);
        layouts = new ConcurrentHashMap<>();
        displayGlass = new ConcurrentHashMap<>();
        for (var block : BuiltInRegistries.BLOCK) {
            // CRH2 shells are wrapped immediately before their section/Flywheel mesh is captured.
            if (!HexieBridges.signBlock().passengerDisplayPanel().supports(block) || block instanceof CRH2PartBlockView) continue;
            for (var state : block.getStateDefinition().getPossibleStates()) {
                var key = BlockModelShaper.stateToModelLocation(state);
                event.getModels().computeIfPresent(key, (ignored, original) -> wrap(original, state));
            }
        }
    }

    public static Layout layout(BlockState state) { return layouts.get(state); }

    /** Resource-baked display faces in block-local coordinates; excludes the carriage's window glass. */
    public static List<BakedQuad> glass(BlockState state) { return displayGlass.getOrDefault(state, List.of()); }

    public static BakedModel wrap(BakedModel original, BlockState state) {
        if (original instanceof MountedModel || !HexieBridges.signBlock().passengerDisplayPanel().supports(state.getBlock())
                || !state.hasProperty(HexieBridges.signBlock().panelDisplayMount().property()) || !HexieBridges.signBlock().panelDisplayColumn().isValid(state)) return original;
        var mount = state.getValue(HexieBridges.signBlock().panelDisplayMount().property());
        if (!HexieBridges.signBlock().panelDisplayMount().hasSign(mount) && !HexieBridges.signBlock().panelDisplayMount().hasNumber(mount)) return original;
        var source = faces(original, state);
        var plane = exterior(source, state);
        if (plane == null) return original;
        var openings = new ArrayList<Opening>();
        double handed = HexieBridges.signBlock().panelDisplayMount().numberLeft(mount) ? 1 : -1;
        Vec3 sign = null, number = null;
        if (HexieBridges.signBlock().panelDisplayMount().hasSign(mount)) {
            var center = plane.center().add(plane.right().scale(handed * 2.2 / 16));
            openings.add(new Opening(center, 9.5 / 16, 2.75 / 16, SIGN));
            sign = center.add(plane.normal().scale(DISPLAY_DEPTH));
        }
        if (HexieBridges.signBlock().panelDisplayMount().hasNumber(mount)) {
            var center = plane.center().add(plane.right().scale(-handed * 5.9 / 16));
            openings.add(new Opening(center, 2.1 / 16, 2.05 / 16, NUMBER));
            number = center.add(plane.normal().scale(DISPLAY_DEPTH));
        }
        layouts.put(state, new Layout(sign, number, plane.right(), plane.up(), plane.normal()));
        var result = new ArrayList<Face>();
        for (var face : source) {
            if (plane.layered()) {
                for (var clipped : cut(face.quad, plane, openings, true)) result.add(new Face(clipped, face.layer));
            } else if (onExterior(face.quad, plane)) {
                for (var clipped : cut(face.quad, plane, openings)) result.add(new Face(clipped, face.layer));
            } else result.add(face);
        }
        var deferredGlass = new ArrayList<BakedQuad>();
        for (var opening : openings) for (String layer : List.of("body", "glass")) {
            var partial = screenModels.get(opening.model + "_" + layer);
            if (partial == null) throw new IllegalStateException("Passenger display models were not prepared before shell baking");
            for (var face : faces(partial, state)) {
                var quad = transform(face.quad, opening.center, plane);
                if (layer.equals("glass") && CarriageTransparencyCompat.required()) deferredGlass.add(quad);
                else result.add(new Face(quad, face.layer));
            }
        }
        if (!deferredGlass.isEmpty()) displayGlass.put(state, List.copyOf(deferredGlass));
        return new MountedModel(original, result, deferredGlass);
    }

    public static List<Face> faces(BakedModel model, BlockState state) {
        var result = new ArrayList<Face>();
        var random = RandomSource.create(42);
        for (var layer : model.getRenderTypes(state, random, ModelData.EMPTY))
            for (int side = -1; side < 6; side++) {
                random.setSeed(42);
                for (var quad : model.getQuads(state, side < 0 ? null : Direction.values()[side], random,
                        ModelData.EMPTY, layer)) result.add(new Face(quad, layer));
            }
        return result;
    }

    private static final class MountedModel extends BakedModelWrapper<BakedModel> {
        private final Map<RenderType, List<BakedQuad>> layers;
        private final List<BakedQuad> all;
        private final ChunkRenderTypeSet renderTypes;
        MountedModel(BakedModel original, List<Face> faces, List<BakedQuad> deferredGlass) {
            super(original);
            var grouped = new LinkedHashMap<RenderType, List<BakedQuad>>();
            for (var face : faces) grouped.computeIfAbsent(face.layer, ignored -> new ArrayList<>()).add(face.quad);
            var frozen = new LinkedHashMap<RenderType, List<BakedQuad>>();
            grouped.forEach((key, value) -> frozen.put(key, List.copyOf(value)));
            layers = Map.copyOf(frozen);
            // Unlayered item/preview callers still receive the complete authored model.
            var complete = new ArrayList<BakedQuad>();
            for (var face : faces) complete.add(face.quad);
            complete.addAll(deferredGlass);
            all = List.copyOf(complete);
            renderTypes = ChunkRenderTypeSet.of(grouped.keySet());
        }
        @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
            return side == null ? all : List.of();
        }
        @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType layer) {
            return side != null ? List.of() : layer == null ? all : layers.getOrDefault(layer, List.of());
        }
        @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) { return renderTypes; }
    }

}
