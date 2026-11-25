package dev.hexie.renderer.mc;

import dev.hexie.renderer.*;
import java.util.*;
import static org.lwjgl.opengl.GL45C.*;

/** Multi-light forward input for Sodium terrain, Sable ships, and HeXie custom geometry.
 * Uses zero-stack preorder threaded BVHs for both axis-aligned terrain VoxelShape boxes and
 * exact world-space custom triangles (including alpha-tested iron-bar cutout grates), relative
 * to an integer-aligned local origin with a union-AABB early-out for distant terrain pixels. */
public final class ForwardLight {
    public record Box(Vec low, Vec high) {}
    public static final int TARGET_WORLD = 1, TARGET_GEOMETRY = 2, TARGET_ALL = 3, TARGET_BLOCK_EMITTER = 4;
    public record Lamp(Vec position, LightState source, int targetMask) {
        public Lamp(Vec position, LightState source) { this(position, source, TARGET_ALL); }
    }
    public record Sun(Vec direction, Vec color) {
        public static final Sun OFF = new Sun(Vec.ZERO, Vec.ZERO);
    }
    /** cutoutMode: 0 = solid opaque, 1 = iron-bar cutout (even quad tri), 2 = iron-bar cutout (odd quad tri). */
    public record CasterTri(Triangle triangle, int cutoutMode) {}

    /** Reusable pre-built stackless BVH over static terrain boxes to avoid per-frame CPU sorting. */
    public static final class BoxTree {
        final List<Box> boxes;
        final double[] bounds; // 6 doubles per node: lo.xyz, hi.xyz
        final int[] miss;
        final boolean[] leaf;
        final int nodeCount;

        BoxTree(List<Box> boxes, double[] bounds, int[] miss, boolean[] leaf, int nodeCount) {
            this.boxes = boxes;
            this.bounds = bounds;
            this.miss = miss;
            this.leaf = leaf;
            this.nodeCount = nodeCount;
        }

        public int boxCount() { return boxes.size(); }
        public List<Box> boxes() { return boxes; }
    }

    /** Reusable pre-built stackless BVH over static caster triangles to avoid per-frame CPU sorting. */
    public static final class TriTree {
        final CasterTri[] ordered;
        final List<Triangle> plainTris;
        final double[] bounds; // 6 doubles per node: lo.xyz, hi.xyz
        final int[] miss;
        final int[] leafIdx;
        final int nodeCount;

        TriTree(CasterTri[] ordered, List<Triangle> plainTris, double[] bounds, int[] miss, int[] leafIdx, int nodeCount) {
            this.ordered = ordered;
            this.plainTris = plainTris;
            this.bounds = bounds;
            this.miss = miss;
            this.leafIdx = leafIdx;
            this.nodeCount = nodeCount;
        }

        public int triangleCount() { return ordered.length; }
    }

    private static final BoxTree EMPTY_BOX_TREE = new BoxTree(List.of(), new double[0], new int[0], new boolean[0], 0);
    public static final TriTree EMPTY_TRI_TREE = new TriTree(new CasterTri[0], List.of(), new double[0], new int[0], new int[0], 0);

    private static final int[] buffers = new int[3], capacities = new int[3];
    private static int ringIndex, activeBuffer, lampCount, boxCount, triCount;
    private static float[] scratch = new float[0];
    private static Vec origin = Vec.ZERO;
    private static Vec boundMin = Vec.ZERO, boundMax = Vec.ZERO;
    private static int publishedMask;
    private static Sun sun = Sun.OFF;
    private static BoxTree publishedBoxTree = EMPTY_BOX_TREE;
    private static TriTree publishedTriTree = EMPTY_TRI_TREE;
    private static List<Box> casters = List.of();
    private static List<Triangle> casterTriangles = List.of();
    private static List<Lamp> lamps = List.of();

    private ForwardLight() {}

    public static void clear() {
        if (activeBuffer == 0 && lampCount == 0 && publishedMask == 0 && sun == Sun.OFF) return;
        activeBuffer = 0;
        lampCount = 0;
        boxCount = 0;
        triCount = 0;
        origin = Vec.ZERO;
        boundMin = Vec.ZERO;
        boundMax = Vec.ZERO;
        publishedBoxTree = EMPTY_BOX_TREE;
        publishedTriTree = EMPTY_TRI_TREE;
        casters = List.of();
        casterTriangles = List.of();
        lamps = List.of();
        publishedMask = 0;
        sun = Sun.OFF;
    }

    public static void dispose() {
        clear();
        invalidatePrograms();
        maxSsboBindings = 0;
        for (int buffer : buffers) if (buffer != 0) glDeleteBuffers(buffer);
        Arrays.fill(buffers, 0);
        Arrays.fill(capacities, 0);
        ringIndex = 0;
        scratch = new float[0];
    }

    public static BoxTree buildBoxTree(List<Box> boxes) {
        int n = boxes.size();
        if (n == 0) return EMPTY_BOX_TREE;
        Box[] arr = boxes.toArray(new Box[0]);
        int maxNodes = 2 * n;
        double[] bounds = new double[6 * maxNodes];
        int[] miss = new int[maxNodes];
        boolean[] leaf = new boolean[maxNodes];
        int[] cursor = {0};
        buildBoxNodes(arr, 0, n, bounds, miss, leaf, cursor);
        return new BoxTree(List.copyOf(boxes), bounds, miss, leaf, cursor[0]);
    }

    public static TriTree buildTriTree(List<CasterTri> triangles) {
        int tc = triangles.size();
        if (tc == 0) return EMPTY_TRI_TREE;
        int maxTriNodes = 2 * tc;
        double[] triBounds = new double[6 * maxTriNodes];
        int[] triMiss = new int[maxTriNodes];
        int[] triLeafIdx = new int[maxTriNodes];
        CasterTri[] triArr = triangles.toArray(new CasterTri[0]);
        int[] cursor = {0};
        buildTriNodes(triArr, 0, tc, triBounds, triMiss, triLeafIdx, cursor);
        var plain = new ArrayList<Triangle>(tc);
        for (int i = 0; i < tc; i++) plain.add(triArr[i].triangle());
        return new TriTree(triArr, List.copyOf(plain), triBounds, triMiss, triLeafIdx, cursor[0]);
    }

    public static void publish(Vec light, LightState source, List<Box> boxes, int rayBudget) {
        publish(List.of(new Lamp(light, source)), buildBoxTree(boxes), EMPTY_TRI_TREE, rayBudget, Sun.OFF, Vec.ZERO);
    }

    public static void publish(List<Lamp> activeLamps, List<Box> boxes, int rayBudget) {
        publish(activeLamps, buildBoxTree(boxes), EMPTY_TRI_TREE, rayBudget, Sun.OFF, Vec.ZERO);
    }

    public static void publish(List<Lamp> activeLamps, List<Box> boxes, List<Triangle> triangles, int rayBudget) {
        var wrapped = new ArrayList<CasterTri>(triangles.size());
        for (var t : triangles) wrapped.add(new CasterTri(t, 0));
        publish(activeLamps, buildBoxTree(boxes), buildTriTree(wrapped), rayBudget, Sun.OFF, Vec.ZERO);
    }

    public static void publish(List<Lamp> activeLamps, BoxTree boxTree, List<CasterTri> triangles, int rayBudget) {
        publish(activeLamps, boxTree, buildTriTree(triangles), rayBudget, Sun.OFF, Vec.ZERO);
    }

    public static void publish(List<Lamp> activeLamps, BoxTree boxTree, List<CasterTri> triangles, int rayBudget,
                               Sun daylight, Vec geometryOrigin) {
        publish(activeLamps, boxTree, buildTriTree(triangles), rayBudget, daylight, geometryOrigin);
    }

    public static void publish(List<Lamp> activeLamps, BoxTree boxTree, TriTree triTree, int rayBudget,
                               Sun daylight, Vec geometryOrigin) {
        if (activeLamps.isEmpty() && daylight.color().equals(Vec.ZERO)) {
            clear();
            return;
        }
        // Integer-aligned local origin keeps MC integer & 1/16 sub-block VoxelShape coordinates bit-exact in float32.
        Vec first = activeLamps.isEmpty() ? geometryOrigin : activeLamps.get(0).position();
        Vec base = new Vec(Math.floor(first.x()), Math.floor(first.y()), Math.floor(first.z()));
        if (activeBuffer != 0 && boxTree == publishedBoxTree && triTree == publishedTriTree
                && base.equals(origin) && daylight.equals(sun) && activeLamps.equals(lamps)) {
            return;
        }
        int lc = activeLamps.size();
        int boxNodeCount = boxTree.nodeCount;
        int tc = triTree.ordered.length;
        int triNodeCount = triTree.nodeCount;
        double[] triBounds = triTree.bounds;
        int[] triMiss = triTree.miss;
        int[] triLeafIdx = triTree.leafIdx;
        CasterTri[] triArr = triTree.ordered;

        int triDataBase = lc + boxNodeCount + triNodeCount;
        int totalPairs = triDataBase + 2 * tc;
        int needed = 4 + totalPairs * 8;
        if (scratch.length < needed) scratch = new float[Math.max(needed, Math.max(512, scratch.length * 2))];
        float[] data = scratch;
        data[0] = Float.intBitsToFloat(triNodeCount);
        data[1] = Float.intBitsToFloat(triDataBase);
        data[2] = Float.intBitsToFloat(lc);
        data[3] = Float.intBitsToFloat(boxNodeCount);

        double bMinX = Double.POSITIVE_INFINITY, bMinY = Double.POSITIVE_INFINITY, bMinZ = Double.POSITIVE_INFINITY;
        double bMaxX = Double.NEGATIVE_INFINITY, bMaxY = Double.NEGATIVE_INFINITY, bMaxZ = Double.NEGATIVE_INFINITY;
        int mask = 0;
        for (int i = 0; i < lc; i++) {
            var lamp = activeLamps.get(i);
            mask |= lamp.targetMask();
            Vec rel = lamp.position().minus(base);
            double r = lamp.source().radius();
            bMinX = Math.min(bMinX, rel.x() - r);
            bMinY = Math.min(bMinY, rel.y() - r);
            bMinZ = Math.min(bMinZ, rel.z() - r);
            bMaxX = Math.max(bMaxX, rel.x() + r);
            bMaxY = Math.max(bMaxY, rel.y() + r);
            bMaxZ = Math.max(bMaxZ, rel.z() + r);
            Vec rgb = lamp.source().linearRgb().times(lamp.source().intensity());
            int o = 4 + 8 * i;
            data[o] = (float) rel.x();
            data[o + 1] = (float) rel.y();
            data[o + 2] = (float) rel.z();
            data[o + 3] = (float) r;
            data[o + 4] = (float) rgb.x();
            data[o + 5] = (float) rgb.y();
            data[o + 6] = (float) rgb.z();
            data[o + 7] = lamp.targetMask();
        }

        int boxBase = 4 + 8 * lc;
        double bx = base.x(), by = base.y(), bz = base.z();
        for (int i = 0; i < boxNodeCount; i++) {
            int o = boxBase + 8 * i;
            int bi = 6 * i;
            boolean isLeaf = boxTree.leaf[i];
            writeBounds(data, o,
                    boxTree.bounds[bi] - bx, boxTree.bounds[bi + 1] - by, boxTree.bounds[bi + 2] - bz,
                    boxTree.bounds[bi + 3] - bx, boxTree.bounds[bi + 4] - by, boxTree.bounds[bi + 5] - bz,
                    !isLeaf);
            data[o + 3] = (float) boxTree.miss[i];
            data[o + 7] = isLeaf ? 1f : 0f;
        }

        int triNodeBase = boxBase + 8 * boxNodeCount;
        for (int i = 0; i < triNodeCount; i++) {
            int o = triNodeBase + 8 * i;
            int bi = 6 * i;
            writeBounds(data, o,
                    triBounds[bi] - bx, triBounds[bi + 1] - by, triBounds[bi + 2] - bz,
                    triBounds[bi + 3] - bx, triBounds[bi + 4] - by, triBounds[bi + 5] - bz,
                    true);
            data[o + 3] = (float) triMiss[i];
            data[o + 7] = (float) triLeafIdx[i];
        }

        int triDataOffset = 4 + 8 * triDataBase;
        for (int i = 0; i < tc; i++) {
            var ct = triArr[i];
            var t = ct.triangle();
            int o = triDataOffset + 16 * i;
            data[o] = (float) (t.a().x() - bx);
            data[o + 1] = (float) (t.a().y() - by);
            data[o + 2] = (float) (t.a().z() - bz);
            data[o + 3] = 0f;
            data[o + 4] = (float) (t.b().x() - bx);
            data[o + 5] = (float) (t.b().y() - by);
            data[o + 6] = (float) (t.b().z() - bz);
            data[o + 7] = 0f;
            data[o + 8] = (float) (t.c().x() - bx);
            data[o + 9] = (float) (t.c().y() - by);
            data[o + 10] = (float) (t.c().z() - bz);
            data[o + 11] = 0f;
            data[o + 12] = (float) ct.cutoutMode();
            data[o + 13] = 0f;
            data[o + 14] = 0f;
            data[o + 15] = 0f;
        }

        ringIndex = (ringIndex + 1) % buffers.length;
        if (buffers[ringIndex] == 0) buffers[ringIndex] = glCreateBuffers();
        int buffer = buffers[ringIndex];
        if (needed > capacities[ringIndex]) {
            int alloc = Math.max(needed, Math.max(512, capacities[ringIndex] * 2));
            glNamedBufferData(buffer, 4L * alloc, GL_STREAM_DRAW);
            capacities[ringIndex] = alloc;
        }
        float[] upload = needed == data.length ? data : Arrays.copyOf(data, needed);
        glNamedBufferSubData(buffer, 0L, upload);
        activeBuffer = buffer;
        origin = base;
        boundMin = lc == 0 ? Vec.ZERO : new Vec(bMinX, bMinY, bMinZ);
        boundMax = lc == 0 ? Vec.ZERO : new Vec(bMaxX, bMaxY, bMaxZ);
        publishedMask = mask;
        sun = daylight;
        publishedBoxTree = boxTree;
        publishedTriTree = triTree;
        lampCount = lc;
        boxCount = boxTree.boxCount();
        triCount = tc;
        casters = boxTree.boxes;
        casterTriangles = triTree.plainTris;
        lamps = List.copyOf(activeLamps);
    }

    private static void writeBounds(float[] data, int o,
                                    double lx, double ly, double lz,
                                    double hx, double hy, double hz,
                                    boolean conservative) {
        float ax = (float) lx, ay = (float) ly, az = (float) lz;
        float bx = (float) hx, by = (float) hy, bz = (float) hz;
        if (conservative) {
            data[o] = ax > lx ? Math.nextDown(ax) : ax;
            data[o + 1] = ay > ly ? Math.nextDown(ay) : ay;
            data[o + 2] = az > lz ? Math.nextDown(az) : az;
            data[o + 4] = bx < hx ? Math.nextUp(bx) : bx;
            data[o + 5] = by < hy ? Math.nextUp(by) : by;
            data[o + 6] = bz < hz ? Math.nextUp(bz) : bz;
        } else {
            data[o] = ax;
            data[o + 1] = ay;
            data[o + 2] = az;
            data[o + 4] = bx;
            data[o + 5] = by;
            data[o + 6] = bz;
        }
    }

    private static void buildBoxNodes(Box[] items, int start, int end,
                                      double[] bounds, int[] miss, boolean[] leaf, int[] cursor) {
        int idx = cursor[0]++;
        if (end - start == 1) {
            Box b = items[start];
            int o = 6 * idx;
            bounds[o] = b.low.x(); bounds[o + 1] = b.low.y(); bounds[o + 2] = b.low.z();
            bounds[o + 3] = b.high.x(); bounds[o + 4] = b.high.y(); bounds[o + 5] = b.high.z();
            miss[idx] = idx + 1;
            leaf[idx] = true;
            return;
        }
        double lx = Double.POSITIVE_INFINITY, ly = Double.POSITIVE_INFINITY, lz = Double.POSITIVE_INFINITY;
        double hx = Double.NEGATIVE_INFINITY, hy = Double.NEGATIVE_INFINITY, hz = Double.NEGATIVE_INFINITY;
        double clx = Double.POSITIVE_INFINITY, cly = Double.POSITIVE_INFINITY, clz = Double.POSITIVE_INFINITY;
        double chx = Double.NEGATIVE_INFINITY, chy = Double.NEGATIVE_INFINITY, chz = Double.NEGATIVE_INFINITY;
        for (int i = start; i < end; i++) {
            Vec lo = items[i].low, hi = items[i].high;
            if (lo.x() < lx) lx = lo.x(); if (lo.y() < ly) ly = lo.y(); if (lo.z() < lz) lz = lo.z();
            if (hi.x() > hx) hx = hi.x(); if (hi.y() > hy) hy = hi.y(); if (hi.z() > hz) hz = hi.z();
            double cx = lo.x() + hi.x(), cy = lo.y() + hi.y(), cz = lo.z() + hi.z();
            if (cx < clx) clx = cx; if (cy < cly) cly = cy; if (cz < clz) clz = cz;
            if (cx > chx) chx = cx; if (cy > chy) chy = cy; if (cz > chz) chz = cz;
        }
        double sx = chx - clx, sy = chy - cly, sz = chz - clz;
        int axis = sx >= sy && sx >= sz ? 0 : sy >= sz ? 1 : 2;
        int mid = (start + end) >>> 1;
        partitionBoxes(items, start, end - 1, mid, axis);
        buildBoxNodes(items, start, mid, bounds, miss, leaf, cursor);
        buildBoxNodes(items, mid, end, bounds, miss, leaf, cursor);
        int o = 6 * idx;
        bounds[o] = lx; bounds[o + 1] = ly; bounds[o + 2] = lz;
        bounds[o + 3] = hx; bounds[o + 4] = hy; bounds[o + 5] = hz;
        miss[idx] = cursor[0];
        leaf[idx] = false;
    }

    private static void buildTriNodes(CasterTri[] items, int start, int end,
                                      double[] bounds, int[] miss, int[] leafIdx, int[] cursor) {
        int idx = cursor[0]++;
        if (end - start == 1) {
            Triangle t = items[start].triangle();
            Vec lo = t.low(), hi = t.high();
            int o = 6 * idx;
            bounds[o] = lo.x(); bounds[o + 1] = lo.y(); bounds[o + 2] = lo.z();
            bounds[o + 3] = hi.x(); bounds[o + 4] = hi.y(); bounds[o + 5] = hi.z();
            miss[idx] = idx + 1;
            leafIdx[idx] = start;
            return;
        }
        double lx = Double.POSITIVE_INFINITY, ly = Double.POSITIVE_INFINITY, lz = Double.POSITIVE_INFINITY;
        double hx = Double.NEGATIVE_INFINITY, hy = Double.NEGATIVE_INFINITY, hz = Double.NEGATIVE_INFINITY;
        double clx = Double.POSITIVE_INFINITY, cly = Double.POSITIVE_INFINITY, clz = Double.POSITIVE_INFINITY;
        double chx = Double.NEGATIVE_INFINITY, chy = Double.NEGATIVE_INFINITY, chz = Double.NEGATIVE_INFINITY;
        for (int i = start; i < end; i++) {
            Triangle t = items[i].triangle();
            Vec lo = t.low(), hi = t.high();
            if (lo.x() < lx) lx = lo.x(); if (lo.y() < ly) ly = lo.y(); if (lo.z() < lz) lz = lo.z();
            if (hi.x() > hx) hx = hi.x(); if (hi.y() > hy) hy = hi.y(); if (hi.z() > hz) hz = hi.z();
            double cx = lo.x() + hi.x(), cy = lo.y() + hi.y(), cz = lo.z() + hi.z();
            if (cx < clx) clx = cx; if (cy < cly) cly = cy; if (cz < clz) clz = cz;
            if (cx > chx) chx = cx; if (cy > chy) chy = cy; if (cz > chz) chz = cz;
        }
        double sx = chx - clx, sy = chy - cly, sz = chz - clz;
        int axis = sx >= sy && sx >= sz ? 0 : sy >= sz ? 1 : 2;
        int mid = (start + end) >>> 1;
        partitionTris(items, start, end - 1, mid, axis);
        buildTriNodes(items, start, mid, bounds, miss, leafIdx, cursor);
        buildTriNodes(items, mid, end, bounds, miss, leafIdx, cursor);
        int o = 6 * idx;
        bounds[o] = lx; bounds[o + 1] = ly; bounds[o + 2] = lz;
        bounds[o + 3] = hx; bounds[o + 4] = hy; bounds[o + 5] = hz;
        miss[idx] = cursor[0];
        leafIdx[idx] = -1;
    }

    /** Linear-time quickselect partition (zero allocations, no Comparator boxing). */
    private static void partitionBoxes(Box[] a, int lo, int hi, int k, int axis) {
        while (lo < hi) {
            double pivot = boxKey(a[(lo + hi) >>> 1], axis);
            int i = lo, j = hi;
            while (i <= j) {
                while (boxKey(a[i], axis) < pivot) i++;
                while (boxKey(a[j], axis) > pivot) j--;
                if (i <= j) {
                    Box tmp = a[i]; a[i] = a[j]; a[j] = tmp;
                    i++; j--;
                }
            }
            if (k <= j) hi = j;
            else if (k >= i) lo = i;
            else return;
        }
    }

    private static void partitionTris(CasterTri[] a, int lo, int hi, int k, int axis) {
        while (lo < hi) {
            double pivot = triKey(a[(lo + hi) >>> 1], axis);
            int i = lo, j = hi;
            while (i <= j) {
                while (triKey(a[i], axis) < pivot) i++;
                while (triKey(a[j], axis) > pivot) j--;
                if (i <= j) {
                    CasterTri tmp = a[i]; a[i] = a[j]; a[j] = tmp;
                    i++; j--;
                }
            }
            if (k <= j) hi = j;
            else if (k >= i) lo = i;
            else return;
        }
    }

    private static double boxKey(Box b, int axis) {
        return b.low.at(axis) + b.high.at(axis);
    }

    private static double triKey(CasterTri ct, int axis) {
        Triangle t = ct.triangle();
        return t.low().at(axis) + t.high().at(axis);
    }

    public static int usedRays() { return 0; }
    public static int publishedLamps() { return lampCount; }
    public static int publishedBoxes() { return boxCount; }
    public static int publishedTriangles() { return triCount; }

    /** A single packed light is shared by a draw. Certify the entire light/receiver hull
     * with its enclosing AABB. Touching an uncertain caster withholds the increase. */
    public static int blockLight(Vec low, Vec high) {
        if (lamps.isEmpty() || ClientRenderState.engine().route().tier() != RenderSettings.Tier.FULL) return 0;
        double total = 0;
        for (var entry : lamps) {
            if ((entry.targetMask() & TARGET_WORLD) == 0) continue;
            Vec position = entry.position();
            LightState lamp = entry.source();
            Vec lo = Vec.min(position, low), hi = Vec.max(position, high);
            double far2 = 0;
            for (int i = 0; i < 3; i++) {
                double far = Math.max(position.at(i) - lo.at(i), hi.at(i) - position.at(i));
                far2 += far * far;
            }
            if (far2 >= lamp.radius() * lamp.radius()) continue;
            boolean blocked = false;
            for (var b : casters) {
                if (lo.x() <= b.high.x() && hi.x() >= b.low.x() && lo.y() <= b.high.y() && hi.y() >= b.low.y()
                        && lo.z() <= b.high.z() && hi.z() >= b.low.z()) {
                    blocked = true;
                    break;
                }
            }
            if (!blocked) {
                for (var t : casterTriangles) {
                    Vec tLo = t.low(), tHi = t.high();
                    if (lo.x() <= tHi.x() && hi.x() >= tLo.x() && lo.y() <= tHi.y() && hi.y() >= tLo.y()
                            && lo.z() <= tHi.z() && hi.z() >= tLo.z()) {
                        blocked = true;
                        break;
                    }
                }
            }
            if (blocked) continue;
            double mono = lamp.linearRgb().dot(new Vec(.2126, .7152, .0722));
            total += mono * lamp.energy(Math.sqrt(far2), Visibility.VISIBLE, 64);
        }
        return (int) (15 * Math.min(1, total));
    }

    private record ProgramLayout(int uniforms, int attributes, int blocks, int blockIndex, int defaultBinding,
                               BitSet occupiedSlots, int enabled, int receiverMask, int sunDir, int sunColor,
                               int origin, int boundMin, int boundMax, int rotation, int hasNormal) {}

    private static final class ProgramMeta {
        long validatedFrame;
        ProgramLayout layout;
    }

    private static final Map<Integer, ProgramMeta> programs = new HashMap<>();
    private static int maxSsboBindings;

    public static void invalidatePrograms() {
        programs.clear();
    }

    private static ProgramMeta programMeta(int program) {
        long frame = ClientRenderState.engine().route().frameId();
        var cached = programs.get(program);
        if (cached != null && cached.validatedFrame == frame) return cached;
        if (cached == null) {
            cached = new ProgramMeta();
            programs.put(program, cached);
        }
        cached.layout = validateLayout(program, cached.layout);
        cached.validatedFrame = frame;
        return cached;
    }

    private static ProgramLayout validateLayout(int program, ProgramLayout cached) {
        boolean deleted = glGetProgrami(program, GL_DELETE_STATUS) != 0;
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) return null;
        int blocks = glGetProgramInterfacei(program, GL_SHADER_STORAGE_BLOCK, GL_ACTIVE_RESOURCES);
        if (blocks == 0) return null;
        int uniforms = glGetProgrami(program, GL_ACTIVE_UNIFORMS);
        int attributes = glGetProgrami(program, GL_ACTIVE_ATTRIBUTES);
        // Resource counts can collide. Our receiver mask resets to zero on every link.
        // Resolve this one name before reading it: an old location may now be invalid.
        int mask = glGetUniformLocation(program, "hexieReceiverMask");
        if (!deleted && cached != null && cached.uniforms == uniforms && cached.attributes == attributes
                && cached.blocks == blocks && mask >= 0 && cached.receiverMask == mask && glGetUniformi(program, mask) != 0)
            return cached;

        int block = glGetProgramResourceIndex(program, GL_SHADER_STORAGE_BLOCK, "HexieCasters");
        if (block == GL_INVALID_INDEX) return null;
        var occupied = new BitSet();
        int[] property = {GL_BUFFER_BINDING}, binding = {0};
        int defaultBinding = 0;
        for (int i = 0; i < blocks; i++) {
            glGetProgramResourceiv(program, GL_SHADER_STORAGE_BLOCK, i, property, null, binding);
            if (i == block) defaultBinding = binding[0];
            else occupied.set(binding[0]);
        }
        return new ProgramLayout(uniforms, attributes, blocks, block, defaultBinding, occupied,
                glGetUniformLocation(program, "hexieEnabled"), mask, glGetUniformLocation(program, "hexieSunDir"),
                glGetUniformLocation(program, "hexieSunColor"), glGetUniformLocation(program, "hexieOrigin"),
                glGetUniformLocation(program, "hexieBoundMin"), glGetUniformLocation(program, "hexieBoundMax"),
                glGetUniformLocation(program, "hexieRotation"), glGetUniformLocation(program, "hexieHasNormal"));
    }

    public static void draw(Vec camera, float[] rotation, boolean packedNormal, Runnable original) {
        draw(camera, rotation, packedNormal, TARGET_WORLD, original);
    }

    public static void draw(Vec camera, float[] rotation, boolean packedNormal, int receiverMask, Runnable original) {
        long start = receiverMask == TARGET_WORLD ? System.nanoTime() : 0;
        try {
            drawReceiver(camera, rotation, packedNormal, receiverMask, original);
        } finally {
            if (receiverMask == TARGET_WORLD) ClientRenderState.timings.terrainDrawNs += System.nanoTime() - start;
        }
    }

    private static void drawReceiver(Vec camera, float[] rotation, boolean packedNormal, int receiverMask, Runnable original) {
        boolean sunlight = !sun.color().equals(Vec.ZERO);
        if (activeBuffer == 0 || ((publishedMask & receiverMask) == 0 && !sunlight)
                || ClientRenderState.engine().route().tier() != RenderSettings.Tier.FULL) {
            original.run();
            return;
        }
        int program = glGetInteger(GL_CURRENT_PROGRAM);
        ProgramLayout meta = program == 0 ? null : programMeta(program).layout;
        if (meta == null) {
            original.run();
            return;
        }
        if (maxSsboBindings == 0) maxSsboBindings = glGetInteger(GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS);
        int slot = -1;
        for (int i = maxSsboBindings - 1; i >= 0; i--) {
            if ((i < 8 || i > 10) && !meta.occupiedSlots.get(i) && glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, i) == 0) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            original.run();
            return;
        }
        int general = glGetInteger(GL_SHADER_STORAGE_BUFFER_BINDING);
        try {
            glShaderStorageBlockBinding(program, meta.blockIndex, slot);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, slot, activeBuffer);
            glUniform1i(meta.receiverMask, receiverMask);
            if (meta.sunDir >= 0) setVec(meta.sunDir, sun.direction());
            if (meta.sunColor >= 0) setVec(meta.sunColor, sun.color());
            setVec(meta.origin, origin.minus(camera));
            setVec(meta.boundMin, boundMin);
            setVec(meta.boundMax, boundMax);
            if (meta.rotation >= 0) glUniformMatrix3fv(meta.rotation, false, rotation);
            if (meta.hasNormal >= 0) glUniform1i(meta.hasNormal, packedNormal ? 1 : 0);
            glUniform1i(meta.enabled, 1);
            original.run();
        } finally {
            glProgramUniform1i(program, meta.enabled, 0);
            glShaderStorageBlockBinding(program, meta.blockIndex, meta.defaultBinding);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, slot, 0);
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, general);
        }
    }

    private static void setVec(int location, Vec v) {
        glUniform3f(location, (float) v.x(), (float) v.y(), (float) v.z());
    }

    public static final String FRAGMENT = """
        #if defined(GL_ARB_shader_storage_buffer_object) || (__VERSION__ >= 430)
        struct HexiePair { vec4 a; vec4 b; };
        layout(std430) readonly buffer HexieCasters {
            uint hexieTriNodeCount, hexieTriDataBase, hexieLampCount, hexieBoxNodeCount;
            HexiePair hexieData[];
        };
        uniform int hexieEnabled;
        uniform int hexieReceiverMask;
        uniform vec3 hexieSunDir, hexieSunColor;
        uniform vec3 hexieOrigin;
        uniform vec3 hexieBoundMin;
        uniform vec3 hexieBoundMax;
        uniform mat3 hexieRotation;
        bool hexieBoxInFront(vec3 lo, vec3 hi, vec3 p, vec3 n) {
            vec3 farthest = mix(lo, hi, greaterThanEqual(n, vec3(0.0)));
            return dot(farthest - p, n) > 6e-5;
        }
        bool hexieRayBox(vec3 orig, vec3 invDelta, vec3 lo, vec3 hi, bool strictEnd, float sourceExit) {
            vec3 t0 = (lo - orig) * invDelta;
            vec3 t1 = (hi - orig) * invDelta;
            vec3 tMin = min(t0, t1);
            vec3 tMax = max(t0, t1);
            float enter = max(0.0, max(tMin.x, max(tMin.y, tMin.z)));
            float leave = min(1.0, min(tMax.x, min(tMax.y, tMax.z)));
            // A solid emitter can light its exposed faces. Keep the rest of a merged terrain
            // leaf opaque, as well as all triangles and boxes between the source and receiver.
            if (strictEnd && sourceExit >= 0.0 && all(greaterThanEqual(orig, lo)) && all(lessThanEqual(orig, hi))
                    && leave <= sourceExit + 1e-5) return false;
            return strictEnd ? (enter <= leave && enter < 1.0) : (enter <= leave);
        }
        bool hexieGrateOpaque(vec2 uv) {
            const uint rows[16] = uint[16](
                0x318Cu, 0x318Cu, 0x31FCu, 0x31FCu,
                0x318Cu, 0x318Cu, 0x318Cu, 0xF18Fu,
                0xF18Fu, 0x318Cu, 0x318Cu, 0x318Cu,
                0x3F8Cu, 0x3F8Cu, 0x318Cu, 0x318Cu
            );
            int tx = clamp(int(uv.x * 16.0), 0, 15);
            int ty = clamp(int(uv.y * 16.0), 0, 15);
            return ((rows[ty] >> uint(tx)) & 1u) != 0u;
        }
        bool hexieRayTri(vec3 orig, vec3 delta, vec3 a, vec3 b, vec3 c, int cutoutMode, vec3 p, vec3 n) {
            float front = max(dot(a - p, n), max(dot(b - p, n), dot(c - p, n)));
            if (front <= 6e-5) return false;
            vec3 e1 = b - a;
            vec3 e2 = c - a;
            vec3 h = cross(delta, e2);
            float det = dot(e1, h);
            if (abs(det) <= 1e-12) return false;
            float invDet = 1.0 / det;
            vec3 s = orig - a;
            float u = dot(s, h) * invDet;
            if (u < -1e-6 || u > 1.0 + 1e-6) return false;
            vec3 q = cross(s, e1);
            float v = dot(delta, q) * invDet;
            if (v < -1e-6 || u + v > 1.0 + 1e-6) return false;
            float t = dot(e2, q) * invDet;
            if (t < 0.0 || t >= 1.0) return false;
            if (cutoutMode > 0) {
                vec2 uv = cutoutMode == 1 ? vec2(u + v, v) : vec2(u, u + v);
                if (!hexieGrateOpaque(uv)) return false;
            }
            return true;
        }
        bool hexieOccluded(vec3 orig, vec3 delta, vec3 p, vec3 n, bool blockEmitter) {
            vec3 safeDelta = vec3(
                abs(delta.x) > 1e-12 ? delta.x : (delta.x >= 0.0 ? 1e-12 : -1e-12),
                abs(delta.y) > 1e-12 ? delta.y : (delta.y >= 0.0 ? 1e-12 : -1e-12),
                abs(delta.z) > 1e-12 ? delta.z : (delta.z >= 0.0 ? 1e-12 : -1e-12));
            vec3 invDelta = 1.0 / safeDelta;
            float sourceExit = -1.0;
            if (blockEmitter) {
                vec3 edge = floor(orig) + mix(vec3(0.0), vec3(1.0), greaterThanEqual(delta, vec3(0.0)));
                vec3 t = (edge - orig) * invDelta;
                sourceExit = min(t.x, min(t.y, t.z));
            }
            uint idx = 0u;
            while (idx < hexieBoxNodeCount) {
                HexiePair node = hexieData[hexieLampCount + idx];
                bool isLeaf = node.b.w > 0.5;
                if (hexieBoxInFront(node.a.xyz, node.b.xyz, p, n)
                        && hexieRayBox(orig, invDelta, node.a.xyz, node.b.xyz, isLeaf, sourceExit)) {
                    if (isLeaf) return true;
                    idx++;
                } else {
                    idx = uint(node.a.w);
                }
            }
            if (hexieTriNodeCount > 0u) {
                uint triNodeBase = hexieLampCount + hexieBoxNodeCount;
                uint tidx = 0u;
                while (tidx < hexieTriNodeCount) {
                    HexiePair node = hexieData[triNodeBase + tidx];
                    if (hexieBoxInFront(node.a.xyz, node.b.xyz, p, n)
                            && hexieRayBox(orig, invDelta, node.a.xyz, node.b.xyz, false, -1.0)) {
                        int triIdx = int(node.b.w);
                        if (triIdx >= 0) {
                            uint base = hexieTriDataBase + 2u * uint(triIdx);
                            HexiePair p0 = hexieData[base];
                            HexiePair p1 = hexieData[base + 1u];
                            if (hexieRayTri(orig, delta, p0.a.xyz, p0.b.xyz, p1.a.xyz, int(p1.b.x), p, n)) return true;
                            tidx = uint(node.a.w);
                        } else {
                            tidx++;
                        }
                    } else {
                        tidx = uint(node.a.w);
                    }
                }
            }
            return false;
        }
        bool hexieOccluded(vec3 orig, vec3 delta, vec3 p, vec3 n) {
            return hexieOccluded(orig, delta, p, n, false);
        }
        float hexieRoofShade(vec3 p, vec3 nrm) {
            if (hexieEnabled == 0 || dot(p, p) >= 40.0 * 40.0) return 1.0;
            float nLen2 = dot(nrm, nrm);
            vec3 nn = nLen2 > 1e-12 ? nrm * inversesqrt(nLen2) : vec3(0.0, 1.0, 0.0);
            vec3 fragRel = hexieRotation * p - hexieOrigin;
            // Offset 0.25 blocks along surface normal so recessed exterior doors step outside tiny overhangs,
            // while interior walls/seats/floors step deeper inside the carriage under its roof.
            vec3 pRoof = fragRel + nn * 0.25;
            vec3 up = vec3(0.0, 16.0, 0.0);
            return hexieOccluded(pRoof + up, -up, pRoof, vec3(0.0, 1.0, 0.0)) ? 0.55 : 1.0;
        }
        vec3 hexieDaylight(vec3 p, vec3 albedo, vec3 nrm, float sky) {
            if (hexieEnabled == 0 || sky <= 0.0
                    || !any(greaterThan(hexieSunColor, vec3(0.0)))) return vec3(0.0);
            float nLen2 = dot(nrm, nrm);
            if (!(nLen2 > 1e-12)) return vec3(0.0);
            vec3 nn = nrm * inversesqrt(nLen2);
            float cosine = dot(nn, hexieSunDir);
            if (cosine <= 0.0) return vec3(0.0);
            if (dot(p, p) < 40.0 * 40.0) {
                vec3 fragRel = hexieRotation * p - hexieOrigin;
                vec3 pBias = fragRel + nn * 2.5e-3;
                vec3 reach = hexieSunDir * 32.0;
                if (hexieOccluded(pBias + reach, -reach, pBias, nn)) return vec3(0.0);
            }
            return albedo * hexieSunColor * (cosine * sky);
        }
        vec3 hexieDirect(vec3 p, vec3 albedo, vec3 normal) {
            if (hexieEnabled == 0 || hexieLampCount == 0u) return vec3(0);
            vec3 fragRel = hexieRotation * p - hexieOrigin;
            if (any(lessThan(fragRel, hexieBoundMin)) || any(greaterThan(fragRel, hexieBoundMax))) return vec3(0);
            float nLen2 = dot(normal, normal);
            if (!(nLen2 > 1e-12)) return vec3(0);
            vec3 nrm = normal * inversesqrt(nLen2);
            vec3 pBias = fragRel + nrm * 2.5e-3;
            vec3 sum = vec3(0);
            for (uint li = 0u; li < hexieLampCount; li++) {
                int target = int(hexieData[li].b.w);
                if ((target & hexieReceiverMask) == 0) continue;
                vec4 pr = hexieData[li].a;
                vec3 rgb = hexieData[li].b.xyz;
                vec3 delta = pBias - pr.xyz;
                float d2 = dot(delta, delta);
                if (!(d2 > 1e-10) || d2 >= pr.w * pr.w) continue;
                float d = sqrt(d2);
                float cosine = max(0.0, dot(nrm, -delta / d));
                if (cosine <= 0.0) continue;
                if (!hexieOccluded(pr.xyz, delta, pBias, nrm, (target & 4) != 0)) {
                    float atten = (1.0 - smoothstep(14.0, 15.0, 15.0 * d / pr.w)) / max(d2, 1.0);
                    sum += albedo * rgb * (cosine * atten);
                }
            }
            if (any(isnan(sum)) || any(isinf(sum))) return vec3(0);
            return sum;
        }
        #endif
        """;

    public static String fragment(String source, String before, String albedo, String normal) {
        int version = source.indexOf('\n');
        if (version < 0 || !source.contains(before) || !source.contains("void main()")) return source;
        return (source.substring(0, version + 1) + "#ifdef GL_ARB_shader_storage_buffer_object\n"
                + "#extension GL_ARB_shader_storage_buffer_object : enable\n#endif\n" + source.substring(version + 1))
                .replace("void main()", "in vec3 hexiePos, hexieTint;\n" + FRAGMENT + "\nvoid main()")
                .replace(before, "\n#ifdef GL_ARB_shader_storage_buffer_object\n"
                        + "color.rgb += hexieDirect(hexiePos," + albedo + "," + normal + ");\n#endif\n" + before);
    }
}
