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
    record Grid(int x, int y, int z, int width, int height, int depth) {
        static Grid covering(AABB box) {
            int x = (int) Math.floor(box.minX) - 4, y = (int) Math.floor(box.minY) - 4, z = (int) Math.floor(box.minZ) - 4;
            int w = (int) Math.floor(box.maxX) + 5 - x, h = (int) Math.floor(box.maxY) + 5 - y, d = (int) Math.floor(box.maxZ) + 5 - z;
            return new Grid(x + Math.max(0, w - 64) / 2, y + Math.max(0, h - 48) / 2, z + Math.max(0, d - 64) / 2,
                    Math.min(w, 64), Math.min(h, 48), Math.min(d, 64));
        }

        boolean enclosesInner(AABB box) {
            return box.minX >= x + 1 && box.minY >= y + 1 && box.minZ >= z + 1
                    && box.maxX <= x + width - 1 && box.maxY <= y + height - 1 && box.maxZ <= z + depth - 1;
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
        Geometry geometry = bounds(scene);
        if (world == null || geometry == null) return 0;
        boolean moved = grid == null || !grid.enclosesInner(geometry.union);
        Grid next = moved ? Grid.covering(geometry.union) : grid;
        long tick = world.getGameTime();
        boolean fullRebuild = world != level || !next.equals(grid) || texture == 0 || samples == null;
        boolean sectionDirty = revision != ClientRenderState.sectionRevision;
        var cell = new BlockPos.MutableBlockPos();
        if (!fullRebuild && !sectionDirty && tick - lastProbe >= 10) {
            lastProbe = tick;
            // Rotate the checked cells so delayed light-engine updates eventually cover every voxel.
            for (int i = probeOffset; i < next.width * next.height * next.depth; i += 64) {
                cell.set(next.x + i % next.width, next.y + i / next.width % next.height, next.z + i / (next.width * next.height));
                if (samples.get(2 * i) != light(world, LightLayer.BLOCK, cell)
                        || samples.get(2 * i + 1) != light(world, LightLayer.SKY, cell)) {
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
                            cell.set(sx0 + dx, sy0 + dy, sz0 + dz);
                            byte bl = light(world, LightLayer.BLOCK, cell);
                            byte sl = light(world, LightLayer.SKY, cell);
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
                    cell.set(next.x + x, next.y + y, next.z + z);
                    samples.put(light(world, LightLayer.BLOCK, cell)).put(light(world, LightLayer.SKY, cell));
                }
        samples.flip();
        upload(texture, next, samples);
        grid = next;
        level = world;
        revision = ClientRenderState.sectionRevision;
        contentRevision++;
        lastProbe = tick;
        return texture;
    }

    private static byte light(ClientLevel world, LightLayer layer, BlockPos cell) {
        return (byte) (17 * world.getBrightness(layer, cell));
    }

    /** Continuous 8-corner trilinear light field matching StaticShellLighting & Flywheel LightShaders.SMOOTH. */
    int vertexLight(ClientLevel world, Vec p, Vec n) {
        if (world == null) return 0;
        double x = p.x() - 0.5, y = p.y() - 0.5, z = p.z() - 0.5;
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
                        pos.set(gx, gy, gz);
                        int packed = LevelRenderer.getLightColor(world, pos);
                        bVal = LightTexture.block(packed);
                        sVal = LightTexture.sky(packed);
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
