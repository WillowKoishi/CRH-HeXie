package dev.hexie.renderer.neoforge;

import dev.hexie.renderer.Triangle;
import dev.hexie.renderer.Vec;
import dev.hexie.renderer.mc.ClientRenderState;
import dev.hexie.renderer.mc.RendererBackend.Snapshot;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.BufferUtils;
import static org.lwjgl.opengl.GL45C.*;

/** Integer world cells, with texel centers at block centers. Shared by all views of a snapshot. */
final class WorldLightVolume {
    private static final int MAX_XZ = 128;
    private static final int MAX_Y = 48;
    private static final int[][] DIRS = {{0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};

    record Grid(int x, int y, int z, int width, int height, int depth) {
        static Grid covering(AABB box, Vec focus) {
            int bx0 = (int) Math.floor(box.minX) - 4, by0 = (int) Math.floor(box.minY) - 4, bz0 = (int) Math.floor(box.minZ) - 4;
            int bw = (int) Math.floor(box.maxX) + 5 - bx0;
            int bh = (int) Math.floor(box.maxY) + 5 - by0;
            int bd = (int) Math.floor(box.maxZ) + 5 - bz0;
            int w = Math.min(bw, MAX_XZ), h = Math.min(bh, MAX_Y), d = Math.min(bd, MAX_XZ);
            int gx = fitAxis(bx0, bw, w, focus != null ? focus.x() : bx0 + bw * 0.5);
            int gy = fitAxis(by0, bh, h, focus != null ? focus.y() : by0 + bh * 0.5);
            int gz = fitAxis(bz0, bd, d, focus != null ? focus.z() : bz0 + bd * 0.5);
            return new Grid(gx, gy, gz, w, h, d);
        }

        private static int fitAxis(int min, int span, int size, double focus) {
            if (span <= size) return min;
            // Snap to 16-block chunk steps around camera so walking across a large railway yard stays smooth!
            int target = ((int) Math.floor(focus - size * 0.5)) & ~15;
            return Math.max(min, Math.min(min + span - size, target));
        }

        boolean covers(AABB box, Vec focus) {
            Grid desired = Grid.covering(box, focus);
            // Always resize if the scene grew beyond the current volume dimensions!
            if (width < desired.width || height < desired.height || depth < desired.depth) return false;
            if (desired.width < MAX_XZ && (box.minX < x + 1 || box.maxX > x + width - 1)) return false;
            if (desired.height < MAX_Y && (box.minY < y + 1 || box.maxY > y + height - 1)) return false;
            if (desired.depth < MAX_XZ && (box.minZ < z + 1 || box.maxZ > z + depth - 1)) return false;
            if (focus != null) {
                if (width == MAX_XZ && desired.x != x && Math.abs(focus.x() - (x + width * 0.5)) > 16.0) return false;
                if (height == MAX_Y && desired.y != y && Math.abs(focus.y() - (y + height * 0.5)) > 10.0) return false;
                if (depth == MAX_XZ && desired.z != z && Math.abs(focus.z() - (z + depth * 0.5)) > 16.0) return false;
            }
            return true;
        }
    }

    private record LocalBounds(List<Triangle> mesh, Vec low, Vec high) {}
    record Geometry(AABB union, List<AABB> groups) {
        double distanceSquared(Vec point) {
            var p = new Vec3(point.x(), point.y(), point.z());
            double distance = Double.POSITIVE_INFINITY;
            for (var group : groups) distance = Math.min(distance, group.distanceToSqr(p));
            return distance;
        }
    }
    private final HashMap<Long, LocalBounds> locals = new HashMap<>();
    private final HashMap<Long, AABB> worldGroupBounds = new HashMap<>();
    private Snapshot snapshot;
    private Geometry bounds;
    private ClientLevel level;
    private Grid grid;
    private Vec casterLow, casterHigh;
    private int texture, probeOffset;
    private long revision, lastProbe, contentRevision;
    private ByteBuffer samples, subSamples;
    private final Set<Long> dirtySectionScratch = new HashSet<>();

    Geometry bounds(Snapshot scene) {
        if (snapshot == scene) return bounds;
        snapshot = scene;
        locals.keySet().retainAll(scene.sections().keySet());
        worldGroupBounds.clear();
        AABB union = null;
        var groups = new ArrayList<AABB>();
        for (long group : scene.sections().keySet()) {
            var pose = scene.world().get(group);
            var mesh = scene.triangles(group);
            if (pose == null || mesh.isEmpty()) continue;
            var local = locals.get(group);
            if (local == null || local.mesh != mesh) {
                Vec min = mesh.getFirst().low(), max = mesh.getFirst().high();
                for (var tri : mesh) {
                    min = Vec.min(min, tri.low());
                    max = Vec.max(max, tri.high());
                }
                local = new LocalBounds(mesh, min, max);
                locals.put(group, local);
            }
            Vec low = null, high = null;
            for (int i = 0; i < 8; i++) {
                Vec p = pose.apply(new Vec((i & 1) == 0 ? local.low.x() : local.high.x(),
                        (i & 2) == 0 ? local.low.y() : local.high.y(), (i & 4) == 0 ? local.low.z() : local.high.z()));
                low = low == null ? p : Vec.min(low, p);
                high = high == null ? p : Vec.max(high, p);
            }
            var groupBox = new AABB(low.x(), low.y(), low.z(), high.x(), high.y(), high.z());
            worldGroupBounds.put(group, groupBox);
            groups.add(groupBox);
            union = union == null ? groupBox : union.minmax(groupBox);
        }
        bounds = union == null ? null : new Geometry(union, List.copyOf(groups));
        return bounds;
    }

    AABB groupBounds(Snapshot scene, long group) {
        bounds(scene);
        return worldGroupBounds.get(group);
    }

    Grid grid() { return grid; }
    long contentRevision() { return contentRevision; }

    void trackCasters(Vec low, Vec high) {
        casterLow = low;
        casterHigh = high;
        trackBounds(grid);
    }

    private void trackBounds(Grid volume) {
        Vec low = casterLow, high = casterHigh;
        if (volume != null) {
            Vec first = new Vec(volume.x, volume.y, volume.z);
            Vec last = new Vec(volume.x + volume.width - 1, volume.y + volume.height - 1, volume.z + volume.depth - 1);
            low = low == null ? first : Vec.min(low, first);
            high = high == null ? last : Vec.max(high, last);
        }
        ClientRenderState.setTrackedBlockBounds((int) low.x(), (int) low.y(), (int) low.z(),
                (int) high.x(), (int) high.y(), (int) high.z());
    }

    int update(ClientLevel world, Snapshot scene) {
        return update(world, scene, null);
    }

    int update(ClientLevel world, Snapshot scene, Vec focus) {
        Geometry geometry = bounds(scene);
        if (world == null || geometry == null) return 0;
        boolean moved = grid == null || !grid.covers(geometry.union, focus);
        Grid next = moved ? Grid.covering(geometry.union, focus) : grid;
        long tick = world.getGameTime();
        boolean fullRebuild = world != level || !next.equals(grid) || texture == 0 || samples == null;
        boolean sectionDirty = revision != ClientRenderState.sectionRevision;
        var cell = new BlockPos.MutableBlockPos();
        if (!fullRebuild && Math.abs(tick - lastProbe) >= 10) {
            lastProbe = tick;
            // Rotate the checked cells so delayed light-engine updates eventually cover every voxel.
            for (int i = probeOffset; i < next.width * next.height * next.depth; i += 64) {
                int packed = samplePacked(world, next.x + i % next.width, next.y + i / next.width % next.height,
                        next.z + i / (next.width * next.height), cell);
                if (samples.get(2 * i) != (byte) (packed & 0xFF)
                        || samples.get(2 * i + 1) != (byte) ((packed >>> 8) & 0xFF)) {
                    fullRebuild = true;
                    break;
                }
            }
            probeOffset = (probeOffset + 1) % 64;
        }
        if (!fullRebuild && !sectionDirty) return texture;

        dirtySectionScratch.clear();
        ClientRenderState.drainDirtyVolumeSections(dirtySectionScratch);
        if (!fullRebuild && !dirtySectionScratch.isEmpty() && dirtySectionScratch.size() <= 8) {
            // Fast incremental sub-region upload: updates ONLY the dirty 16x16x16 section(s) (~0.2 ms vs 40 ms)!
            boolean any = false;
            for (long key : dirtySectionScratch) {
                int sx0 = Math.max(next.x, ClientRenderState.unpackSectionX(key) << 4);
                int sy0 = Math.max(next.y, ClientRenderState.unpackSectionY(key) << 4);
                int sz0 = Math.max(next.z, ClientRenderState.unpackSectionZ(key) << 4);
                int sx1 = Math.min(next.x + next.width, (ClientRenderState.unpackSectionX(key) << 4) + 16);
                int sy1 = Math.min(next.y + next.height, (ClientRenderState.unpackSectionY(key) << 4) + 16);
                int sz1 = Math.min(next.z + next.depth, (ClientRenderState.unpackSectionZ(key) << 4) + 16);
                int sw = sx1 - sx0, sh = sy1 - sy0, sd = sz1 - sz0;
                if (sw <= 0 || sh <= 0 || sd <= 0) continue;
                int bytes = 2 * sw * sh * sd;
                if (subSamples == null || subSamples.capacity() < bytes) {
                    subSamples = BufferUtils.createByteBuffer(Math.max(bytes, 8192));
                }
                subSamples.clear();
                for (int dz = 0; dz < sd; dz++) {
                    int gz = sz0 - next.z + dz;
                    for (int dy = 0; dy < sh; dy++) {
                        int gy = sy0 - next.y + dy;
                        int rowBase = 2 * ((gz * next.height + gy) * next.width + (sx0 - next.x));
                        for (int dx = 0; dx < sw; dx++) {
                            int packed = samplePacked(world, sx0 + dx, sy0 + dy, sz0 + dz, cell);
                            byte bl = (byte) (packed & 0xFF);
                            byte sl = (byte) ((packed >>> 8) & 0xFF);
                            samples.put(rowBase + 2 * dx, bl);
                            samples.put(rowBase + 2 * dx + 1, sl);
                            subSamples.put(bl).put(sl);
                        }
                    }
                }
                subSamples.flip();
                uploadSub(texture, sx0 - next.x, sy0 - next.y, sz0 - next.z, sw, sh, sd, subSamples);
                any = true;
            }
            revision = ClientRenderState.sectionRevision;
            if (any) contentRevision++;
            lastProbe = tick;
            return texture;
        }

        if (!next.equals(grid)) trackBounds(next);
        if (grid == null || grid.width != next.width || grid.height != next.height || grid.depth != next.depth) {
            if (texture != 0) glDeleteTextures(texture);
            texture = glCreateTextures(GL_TEXTURE_3D);
            glTextureStorage3D(texture, 1, GL_RG8, next.width, next.height, next.depth);
            glTextureParameteri(texture, GL_TEXTURE_BASE_LEVEL, 0);
            glTextureParameteri(texture, GL_TEXTURE_MAX_LEVEL, 0);
            glTextureParameteri(texture, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTextureParameteri(texture, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            for (int axis : new int[]{GL_TEXTURE_WRAP_S, GL_TEXTURE_WRAP_T, GL_TEXTURE_WRAP_R})
                glTextureParameteri(texture, axis, GL_CLAMP_TO_EDGE);
            int bytes = 2 * next.width * next.height * next.depth;
            if (samples == null || samples.capacity() < bytes) samples = BufferUtils.createByteBuffer(bytes);
        }
        samples.clear();
        for (int z = 0; z < next.depth; z++)
            for (int y = 0; y < next.height; y++)
                for (int x = 0; x < next.width; x++) {
                    int packed = samplePacked(world, next.x + x, next.y + y, next.z + z, cell);
                    samples.put((byte) (packed & 0xFF)).put((byte) ((packed >>> 8) & 0xFF));
                }
        samples.flip();
        upload(texture, next, samples);
        grid = next;
        level = world;
        revision = ClientRenderState.sectionRevision;
        contentRevision++;
        lastProbe = tick;
        int fx = focus != null ? (int) Math.floor(focus.x()) : next.x + next.width / 2;
        int fy = focus != null ? (int) Math.floor(focus.y()) : next.y + next.height / 2;
        int fz = focus != null ? (int) Math.floor(focus.z()) : next.z + next.depth / 2;
        int fp = samplePacked(world, fx, fy, fz, cell);
        ClientRenderState.volumeDebug = String.format("tex=%d [%d..%d,%d..%d,%d..%d] %dx%dx%d focus(%d,%d,%d)=b%d/s%d",
                texture, next.x, next.x + next.width, next.y, next.y + next.height, next.z, next.z + next.depth,
                next.width, next.height, next.depth, fx, fy, fz, (fp & 0xFF) / 17, ((fp >>> 8) & 0xFF) / 17);
        return texture;
    }

    /** Samples RG8-scaled (block | sky<<8) light; solid opaque blocks inherit max open-neighbor light so GL_LINEAR
     * never bleeds pitch-black (0,0) upward out of grass/dirt/track floors into carriage skirts or floors! */
    private static int samplePacked(ClientLevel world, int x, int y, int z, BlockPos.MutableBlockPos cell) {
        cell.set(x, y, z);
        int b = world.getBrightness(LightLayer.BLOCK, cell);
        int s = world.getBrightness(LightLayer.SKY, cell);
        if (b == 0 && s == 0 && world.getBlockState(cell).isSolidRender(world, cell)) {
            for (int[] d : DIRS) {
                cell.set(x + d[0], y + d[1], z + d[2]);
                if (world.getBlockState(cell).isSolidRender(world, cell)) continue;
                int nb = world.getBrightness(LightLayer.BLOCK, cell);
                int ns = world.getBrightness(LightLayer.SKY, cell);
                if (nb > b) b = nb;
                if (ns > s) s = ns;
            }
        }
        return (17 * b) | ((17 * s) << 8);
    }

    /** Continuous 8-corner trilinear light field matching StaticShellLighting & Flywheel LightShaders.SMOOTH. */
    int vertexLight(ClientLevel world, Vec p, Vec n) {
        if (world == null) return 0;
        double ox = n != null ? n.x() * 0.15 : 0.0;
        double oy = n != null ? n.y() * 0.15 : 0.0;
        double oz = n != null ? n.z() * 0.15 : 0.0;
        double x = p.x() + ox - 0.5, y = p.y() + oy - 0.5, z = p.z() + oz - 0.5;
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
        double fx = x - bx, fy = y - by, fz = z - bz;
        double block = 0.0, sky = 0.0;
        var pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 2; dx++) {
            double wx = dx == 0 ? (1.0 - fx) : fx;
            int gx = bx + dx;
            for (int dy = 0; dy < 2; dy++) {
                double wxy = wx * (dy == 0 ? (1.0 - fy) : fy);
                int gy = by + dy;
                for (int dz = 0; dz < 2; dz++) {
                    double w = wxy * (dz == 0 ? (1.0 - fz) : fz);
                    int gz = bz + dz;
                    int bVal, sVal;
                    if (grid != null && level == world
                            && gx >= grid.x && gx < grid.x + grid.width
                            && gy >= grid.y && gy < grid.y + grid.height
                            && gz >= grid.z && gz < grid.z + grid.depth) {
                        int i = 2 * (((gz - grid.z) * grid.height + (gy - grid.y)) * grid.width + (gx - grid.x));
                        bVal = Byte.toUnsignedInt(samples.get(i)) / 17;
                        sVal = Byte.toUnsignedInt(samples.get(i + 1)) / 17;
                    } else {
                        int packed = samplePacked(world, gx, gy, gz, pos);
                        bVal = (packed & 0xFF) / 17;
                        sVal = ((packed >>> 8) & 0xFF) / 17;
                    }
                    block += bVal * w;
                    sky += sVal * w;
                }
            }
        }
        return LightTexture.pack((int) Math.round(block), (int) Math.round(sky));
    }

    static void upload(int texture, Grid grid, ByteBuffer data) {
        uploadSub(texture, 0, 0, 0, grid.width, grid.height, grid.depth, data);
    }

    static void uploadSub(int texture, int xOff, int yOff, int zOff, int width, int height, int depth, ByteBuffer data) {
        int pbo = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        int[] fields = {GL_UNPACK_ALIGNMENT, GL_UNPACK_ROW_LENGTH, GL_UNPACK_IMAGE_HEIGHT,
                GL_UNPACK_SKIP_PIXELS, GL_UNPACK_SKIP_ROWS, GL_UNPACK_SKIP_IMAGES};
        int[] saved = new int[fields.length];
        for (int i = 0; i < fields.length; i++) saved[i] = glGetInteger(fields[i]);
        try {
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
            for (int field : fields) glPixelStorei(field, field == GL_UNPACK_ALIGNMENT ? 1 : 0);
            glTextureSubImage3D(texture, 0, xOff, yOff, zOff, width, height, depth, GL_RG, GL_UNSIGNED_BYTE, data);
        } finally {
            for (int i = 0; i < fields.length; i++) glPixelStorei(fields[i], saved[i]);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pbo);
        }
    }

    void dispose() {
        if (texture != 0) glDeleteTextures(texture);
        texture = 0;
        grid = null;
        casterLow = casterHigh = null;
        ClientRenderState.clearTrackedBounds();
        samples = null;
        level = null;
        snapshot = null;
        bounds = null;
        locals.clear();
        worldGroupBounds.clear();
        contentRevision++;
    }
}
