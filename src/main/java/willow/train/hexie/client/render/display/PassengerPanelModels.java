package willow.train.hexie.client.render.display;

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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import willow.train.hexie.Main;
import willow.train.hexie.blocks.emu.crh2a.CRH2PartBlock;
import willow.train.hexie.blocks.signs.PanelDisplayColumn;
import willow.train.hexie.blocks.signs.PanelDisplayMount;
import willow.train.hexie.blocks.signs.PassengerDisplayPanel;
import willow.train.hexie.client.render.carriage.CarriageTransparencyCompat;

/** State-only shell apertures shared by terrain, snapshots and carriage meshes. */
public final class PassengerPanelModels {
    /** Display-plane centers in final block coordinates; an absent screen has a null origin. */
    public record Layout(Vec3 signOrigin, Vec3 numberOrigin, Vec3 right, Vec3 up, Vec3 normal) {}

    private record Face(BakedQuad quad, RenderType layer) {}
    private record Plane(Vec3 center, Vec3 right, Vec3 up, Vec3 normal, boolean layered) {
        Plane(Vec3 center, Vec3 right, Vec3 up, Vec3 normal) { this(center, right, up, normal, false); }
    }
    private record Opening(Vec3 center, double width, double height, String model) {}
    private static final String SIGN = "crh2a_electronic_signage";
    private static final String NUMBER = "crh2a_carriage_number_display";
    private static final double DISPLAY_DEPTH = -.36 / 16;
    private static final double EPS = 1e-6;
    private static volatile Map<String, BakedModel> screenModels = Map.of();
    private static volatile Map<BlockState, Layout> layouts = new ConcurrentHashMap<>();
    private static volatile Map<BlockState, List<BakedQuad>> displayGlass = new ConcurrentHashMap<>();

    private PassengerPanelModels() {}

    public static void register() {
        Main.bus.addListener(PassengerPanelModels::additional);
        // Prepare the partial models before CRH2StaticSections captures its immutable meshes.
        Main.bus.addListener(EventPriority.HIGH, PassengerPanelModels::bake);
    }

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(
                Main.MOD_ID, "block/crh2a/displays/" + name));
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
            if (!PassengerDisplayPanel.supports(block) || block instanceof CRH2PartBlock) continue;
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
        if (original instanceof MountedModel || !PassengerDisplayPanel.supports(state.getBlock())
                || !state.hasProperty(PanelDisplayMount.PROPERTY) || !PanelDisplayColumn.isValid(state)) return original;
        var mount = state.getValue(PanelDisplayMount.PROPERTY);
        if (!mount.hasSign() && !mount.hasNumber()) return original;
        var source = faces(original, state);
        var plane = exterior(source, state);
        if (plane == null) return original;
        var openings = new ArrayList<Opening>();
        double handed = mount.numberLeft() ? 1 : -1;
        Vec3 sign = null, number = null;
        if (mount.hasSign()) {
            var center = plane.center.add(plane.right.scale(handed * 2.2 / 16));
            openings.add(new Opening(center, 9.5 / 16, 2.75 / 16, SIGN));
            sign = center.add(plane.normal.scale(DISPLAY_DEPTH));
        }
        if (mount.hasNumber()) {
            var center = plane.center.add(plane.right.scale(-handed * 5.9 / 16));
            openings.add(new Opening(center, 2.1 / 16, 2.05 / 16, NUMBER));
            number = center.add(plane.normal.scale(DISPLAY_DEPTH));
        }
        layouts.put(state, new Layout(sign, number, plane.right, plane.up, plane.normal));
        var result = new ArrayList<Face>();
        for (var face : source) {
            if (plane.layered) {
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

    private static List<Face> faces(BakedModel model, BlockState state) {
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

    private static Plane exterior(List<Face> faces, BlockState state) {
        if (faces.isEmpty() || !state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return null;
        // Both old carriage panels and CRH2 parts use the opposite of FACING as their painted exterior.
        var outward = Vec3.atLowerCornerOf(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite().getNormal());
        return exterior(faces, outward, PanelDisplayColumn.get(state).offset());
    }

    private static Plane exterior(List<Face> faces, Vec3 outward) {
        return exterior(faces, outward, 0);
    }

    private static Plane exterior(List<Face> faces, Vec3 outward, int column) {
        var right = new Vec3(outward.z, 0, -outward.x);
        double left = Double.POSITIVE_INFINITY, rightmost = Double.NEGATIVE_INFINITY, top = Double.NEGATIVE_INFINITY;
        for (var face : faces) for (var vertex : vertices(face.quad)) {
            top = Math.max(top, vertex.position().y);
            double horizontal = vertex.position().dot(right);
            left = Math.min(left, horizontal); rightmost = Math.max(rightmost, horizontal);
        }
        // Columns are physical cell offsets along the readable exterior-right axis, including mirrored panels.
        double y = top - .25, horizontal = new Vec3(.5, 0, .5).dot(right) + column;
        Plane selected = null;
        double depth = Double.NEGATIVE_INFINITY;
        for (var face : faces) {
            var vertices = vertices(face.quad);
            var normal = normal(vertices);
            if (normal.dot(outward) < .9) continue;
            double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
            for (var vertex : vertices) { low = Math.min(low, vertex.position().y); high = Math.max(high, vertex.position().y); }
            if (y < low - EPS || y > high + EPS) continue;
            double constant = normal.dot(vertices.getFirst().position());
            if (!coversPair(faces, normal, constant, right, horizontal, y)) continue;
            var center = right.scale(horizontal).add(0, y, 0);
            double candidate = (constant - normal.dot(center)) / normal.dot(outward);
            if (candidate <= depth) continue;
            depth = candidate;
            selected = new Plane(center.add(outward.scale(candidate)), right,
                    normal.cross(right).normalize(), normal);
        }
        if (selected != null) return selected;
        // Sealed windows have coplanar painted jambs around a stepped pane and narrow frames.
        // Anchor to both outer jambs, not the projecting frame or the recessed glass.
        for (var face : faces) {
            var vertices = vertices(face.quad);
            var normal = normal(vertices);
            if (normal.dot(outward) < .9) continue;
            double constant = normal.dot(vertices.getFirst().position());
            var up = normal.cross(right).normalize();
            boolean supported = true;
            for (double row : new double[] {y - up.y * 2.75 / 32, y, y + up.y * 2.75 / 32}) {
                var jambs = rowIntervals(faces, normal, constant, right, row);
                var surface = rowIntervals(faces, normal, Double.NaN, right, row);
                if (!coversRange(jambs, left + EPS, left + EPS, EPS)
                        || !coversRange(jambs, rightmost - EPS, rightmost - EPS, EPS)
                        || !coversRange(surface, horizontal - 6.95 / 16, horizontal + 6.95 / 16, 1 / 64d)) {
                    supported = false; break;
                }
            }
            if (!supported) continue;
            var center = right.scale(horizontal).add(0, y, 0);
            double candidate = (constant - normal.dot(center)) / normal.dot(outward);
            if (candidate <= depth) continue;
            depth = candidate;
            selected = new Plane(center.add(outward.scale(candidate)), right, up, normal, true);
        }
        return selected;
    }

    /** A trim strip must not move both displays onto its extended, but nonexistent, plane. */
    private static boolean coversPair(List<Face> faces, Vec3 normal, double constant,
                                      Vec3 right, double horizontal, double y) {
        double halfHeight = normal.cross(right).normalize().y * 2.75 / 32;
        for (double row : new double[] {y - halfHeight, y, y + halfHeight})
            if (!coversRow(faces, normal, constant, right, horizontal, row)) return false;
        return true;
    }

    private static boolean coversRow(List<Face> faces, Vec3 normal, double constant,
                                     Vec3 right, double horizontal, double y) {
        return coversRange(rowIntervals(faces, normal, constant, right, y),
                horizontal - 6.95 / 16, horizontal + 6.95 / 16, EPS);
    }

    private static List<double[]> rowIntervals(List<Face> faces, Vec3 normal, double constant, Vec3 right, double y) {
        var intervals = new ArrayList<double[]>();
        for (var face : faces) {
            var vertices = vertices(face.quad);
            if (normal(vertices).dot(normal) < .9999) continue;
            if (!Double.isNaN(constant)
                    && vertices.stream().anyMatch(v -> Math.abs(normal.dot(v.position()) - constant) > EPS)) continue;
            double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
            var previous = vertices.getLast().position();
            for (var vertex : vertices) {
                var current = vertex.position();
                if (Math.abs(current.y - y) < EPS) {
                    double at = current.dot(right); low = Math.min(low, at); high = Math.max(high, at);
                }
                if ((current.y < y && previous.y > y) || (current.y > y && previous.y < y)) {
                    double amount = (y - previous.y) / (current.y - previous.y);
                    double at = previous.lerp(current, amount).dot(right);
                    low = Math.min(low, at); high = Math.max(high, at);
                }
                previous = current;
            }
            if (high >= low) intervals.add(new double[] {low, high});
        }
        intervals.sort(java.util.Comparator.comparingDouble(interval -> interval[0]));
        return intervals;
    }

    private static boolean coversRange(List<double[]> intervals, double from, double target, double tolerance) {
        double covered = from;
        for (var interval : intervals) {
            if (interval[1] < from - tolerance) continue;
            if (interval[0] > covered + tolerance) break;
            covered = Math.max(covered, interval[1]);
            if (covered >= target - tolerance) return true;
        }
        return false;
    }

    private static boolean onExterior(BakedQuad quad, Plane plane) {
        var vertices = vertices(quad);
        if (normal(vertices).dot(plane.normal) < .9999) return false;
        for (var vertex : vertices)
            if (Math.abs(vertex.position().subtract(plane.center).dot(plane.normal)) > EPS) return false;
        return true;
    }

    private static Vec3 normal(List<Vertex> vertices) {
        for (int i = 1; i + 1 < vertices.size(); i++) {
            var normal = vertices.get(i).position().subtract(vertices.getFirst().position())
                    .cross(vertices.get(i + 1).position().subtract(vertices.getFirst().position()));
            if (normal.lengthSqr() > 1e-18) return normal.normalize();
        }
        return Vec3.ZERO;
    }

    private static List<Vertex> vertices(BakedQuad quad) {
        var result = new ArrayList<Vertex>(4);
        for (int i = 0; i < 4; i++) result.add(Vertex.read(quad.getVertices(), i));
        return result;
    }

    private static List<BakedQuad> cut(BakedQuad quad, Plane plane, List<Opening> openings) {
        return cut(quad, plane, openings, false);
    }

    private static List<BakedQuad> cut(BakedQuad quad, Plane plane, List<Opening> openings, boolean layered) {
        var vertices = vertices(quad);
        if (layered && vertices.stream().allMatch(v ->
                v.position().subtract(plane.center).dot(plane.normal) <= -.65 / 16 + EPS)) return List.of(quad);
        if (openings.stream().noneMatch(opening -> overlaps(vertices, plane, opening))) return List.of(quad);
        List<List<Vertex>> polygons = new ArrayList<>();
        polygons.add(List.of(vertices.get(0), vertices.get(1), vertices.get(2)));
        polygons.add(List.of(vertices.get(0), vertices.get(2), vertices.get(3)));
        for (var opening : openings) {
            var remaining = new ArrayList<List<Vertex>>();
            for (var polygon : polygons) {
                if (!overlaps(polygon, plane, opening)) { remaining.add(polygon); continue; }
                // Peel disjoint outside bands. For a stepped window the final half-space
                // also keeps every face behind the case, including the original interior skin.
                var inside = polygon;
                Vec3[] axes = {plane.right, plane.right, plane.up, plane.up, plane.normal};
                double[] limits = {-opening.width / 2, opening.width / 2, -opening.height / 2, opening.height / 2, -.65 / 16};
                for (int i = 0; i < (layered ? 5 : 4) && !inside.isEmpty(); i++) {
                    boolean above = i % 2 == 0;
                    var outside = clip(inside, opening.center, axes[i], limits[i], !above);
                    if (outside.size() >= 3) remaining.add(outside);
                    inside = clip(inside, opening.center, axes[i], limits[i], above);
                }
            }
            polygons = remaining;
        }
        var result = new ArrayList<BakedQuad>();
        for (var polygon : polygons) for (int i = 1; i + 1 < polygon.size(); i++) {
            var a = polygon.get(0); var b = polygon.get(i); var c = polygon.get(i + 1);
            if (b.position().subtract(a.position()).cross(c.position().subtract(a.position())).lengthSqr() < 1e-18) continue;
            int[] packed = new int[32];
            a.write(packed, 0); b.write(packed, 1); c.write(packed, 2); c.write(packed, 3);
            result.add(new BakedQuad(packed, quad.getTintIndex(), quad.getDirection(), quad.getSprite(),
                    quad.isShade(), quad.hasAmbientOcclusion()));
        }
        return result;
    }

    private static boolean overlaps(List<Vertex> polygon, Plane plane, Opening opening) {
        double minX = Double.POSITIVE_INFINITY, minY = minX, maxX = Double.NEGATIVE_INFINITY, maxY = maxX;
        for (var vertex : polygon) {
            var delta = vertex.position().subtract(opening.center);
            double x = delta.dot(plane.right), y = delta.dot(plane.up);
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
        }
        return minX < opening.width / 2 - EPS && maxX > -opening.width / 2 + EPS
                && minY < opening.height / 2 - EPS && maxY > -opening.height / 2 + EPS;
    }

    private static List<Vertex> clip(List<Vertex> input, Vec3 origin, Vec3 axis, double limit, boolean above) {
        if (input.isEmpty()) return input;
        var output = new ArrayList<Vertex>();
        var previous = input.getLast();
        double previousValue = previous.position().subtract(origin).dot(axis) - limit;
        boolean previousInside = above ? previousValue >= 0 : previousValue <= 0;
        for (var current : input) {
            double value = current.position().subtract(origin).dot(axis) - limit;
            boolean inside = above ? value >= 0 : value <= 0;
            if (inside != previousInside) output.add(previous.lerp(current, previousValue / (previousValue - value)));
            if (inside) output.add(current);
            previous = current; previousValue = value; previousInside = inside;
        }
        return output;
    }

    private static BakedQuad transform(BakedQuad quad, Vec3 center, Plane plane) {
        int[] packed = quad.getVertices().clone();
        int stride = packed.length / 4;
        for (int i = 0; i < 4; i++) {
            int p = i * stride;
            var local = new Vec3(Float.intBitsToFloat(packed[p]), Float.intBitsToFloat(packed[p + 1]), Float.intBitsToFloat(packed[p + 2]));
            var vertex = center.add(vector(local, plane));
            packed[p] = Float.floatToRawIntBits((float)vertex.x);
            packed[p + 1] = Float.floatToRawIntBits((float)vertex.y);
            packed[p + 2] = Float.floatToRawIntBits((float)vertex.z);
            int normal = packed[p + 7];
            var transformed = vector(new Vec3((byte)normal, (byte)(normal >>> 8), (byte)(normal >>> 16)), plane);
            packed[p + 7] = (normal & 0xff000000) | ((int)Math.round(transformed.x) & 255)
                    | (((int)Math.round(transformed.y) & 255) << 8) | (((int)Math.round(transformed.z) & 255) << 16);
        }
        var direction = vector(Vec3.atLowerCornerOf(quad.getDirection().getNormal()), plane);
        return new BakedQuad(packed, quad.getTintIndex(), Direction.getNearest(direction.x, direction.y, direction.z),
                quad.getSprite(), quad.isShade(), quad.hasAmbientOcclusion());
    }

    private static Vec3 vector(Vec3 local, Plane plane) {
        return plane.right.scale(local.x).add(plane.up.scale(local.y)).add(plane.normal.scale(local.z));
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

    private static final class Vertex {
        // Position, UV, RGBA, packed normal components and both lightmap channels.
        final double[] a = new double[14];
        static Vertex read(int[] data, int vertex) {
            var v = new Vertex(); int p = vertex * (data.length / 4);
            for (int i = 0; i < 3; i++) v.a[i] = Float.intBitsToFloat(data[p + i]);
            v.a[3] = Float.intBitsToFloat(data[p + 4]); v.a[4] = Float.intBitsToFloat(data[p + 5]);
            for (int i = 0; i < 4; i++) v.a[5 + i] = (data[p + 3] >>> (8 * i)) & 255;
            for (int i = 0; i < 3; i++) v.a[9 + i] = (byte)(data[p + 7] >>> (8 * i));
            v.a[12] = data[p + 6] & 65535; v.a[13] = data[p + 6] >>> 16;
            return v;
        }
        Vec3 position() { return new Vec3(a[0], a[1], a[2]); }
        Vertex lerp(Vertex other, double amount) {
            var v = new Vertex();
            for (int i = 0; i < a.length; i++) v.a[i] = a[i] + (other.a[i] - a[i]) * amount;
            return v;
        }
        void write(int[] data, int vertex) {
            int p = vertex * 8;
            for (int i = 0; i < 3; i++) data[p + i] = Float.floatToRawIntBits((float)a[i]);
            for (int i = 0; i < 4; i++) data[p + 3] |= ((int)Math.round(a[5 + i]) & 255) << (8 * i);
            data[p + 4] = Float.floatToRawIntBits((float)a[3]); data[p + 5] = Float.floatToRawIntBits((float)a[4]);
            data[p + 6] = ((int)Math.round(a[12]) & 65535) | (((int)Math.round(a[13]) & 65535) << 16);
            for (int i = 0; i < 3; i++) data[p + 7] |= ((int)Math.round(a[9 + i]) & 255) << (8 * i);
        }
    }
}
