package dev.hexie.renderer.mc;

import dev.hexie.renderer.*;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import willow.train.hexie.mc.client.renderbridge.RenderBridge;

public final class ClientRenderState {
    private ClientRenderState() {}

    @FunctionalInterface
    public interface HostFrameProvider {
        void collect(long frameId, float partialTick,
                     List<GroupPose> poses,
                     List<ProceduralDeform> deforms,
                     List<LightState> lights);
    }

    public interface BlockCasterOverride {
        /** Return true to omit this block from coarse VoxelShape box occlusion (e.g. StaticShell, glass, cut window). */
        boolean skipVoxelBox(Object level, int x, int y, int z, Object blockState);
        /** Optionally append exact world-space opaque caster triangles for a skipped block (e.g. custom-cut window). */
        default void appendExtraTriangles(Object level, int x, int y, int z, Object blockState,
                                          List<ForwardLight.CasterTri> out) {}
    }

    @FunctionalInterface
    public interface DynamicCasterProvider {
        /** Appends world-space opaque shadow triangles and moving lamps for dynamic assemblies (e.g. Create trains)
         * and returns a pose/content signature. */
        long collect(Object level, Vec focus, float partialTick, int maxTriangles,
                     List<ForwardLight.CasterTri> outTris, List<ForwardLight.Lamp> outLamps);
    }

    public static DemoSceneController demo;
    public static Path config;
    public static String pack="off", reason="waiting for frame";
    public static int terrainBatches,shipBatches,wrapperMismatches;
    public static int maxTextureSize=4096;
    public static long sectionRevision;
    public static final FrameTimings timings = new FrameTimings();
    private static final Map<String, HostFrameProvider> HOST_PROVIDERS = new ConcurrentHashMap<>();
    private static final Map<String, BlockCasterOverride> CASTER_OVERRIDES = new ConcurrentHashMap<>();
    private static final Map<String, DynamicCasterProvider> DYNAMIC_CASTER_PROVIDERS = new ConcurrentHashMap<>();
    private static final Set<Long> DIRTY_CASTER_SECTIONS = ConcurrentHashMap.newKeySet();
    private static final Set<Long> DIRTY_VOLUME_SECTIONS = ConcurrentHashMap.newKeySet();
    private static boolean trackingActive;
    private static int minSectionX, minSectionY, minSectionZ, maxSectionX, maxSectionY, maxSectionZ;

    public static void registerHostProvider(String id, HostFrameProvider provider) {
        if (provider == null) HOST_PROVIDERS.remove(id);
        else HOST_PROVIDERS.put(id, provider);
    }

    public static void unregisterHostProvider(String id) {
        HOST_PROVIDERS.remove(id);
    }

    public static void registerBlockCasterOverride(String id, BlockCasterOverride override) {
        if (override == null) CASTER_OVERRIDES.remove(id);
        else CASTER_OVERRIDES.put(id, override);
    }

    public static void registerDynamicCasterProvider(String id, DynamicCasterProvider provider) {
        if (provider == null) DYNAMIC_CASTER_PROVIDERS.remove(id);
        else DYNAMIC_CASTER_PROVIDERS.put(id, provider);
    }

    public static long collectDynamicCasterTriangles(Object level, Vec focus, float partialTick,
                                                     int maxTriangles, List<ForwardLight.CasterTri> outTris,
                                                     List<ForwardLight.Lamp> outLamps) {
        long sig = 17L;
        for (var p : DYNAMIC_CASTER_PROVIDERS.values()) {
            sig = sig * 31L + p.collect(level, focus, partialTick, maxTriangles, outTris, outLamps);
        }
        return sig;
    }

    public static boolean skipVoxelBox(Object level, int x, int y, int z, Object blockState) {
        for (var ov : CASTER_OVERRIDES.values()) {
            if (ov.skipVoxelBox(level, x, y, z, blockState)) return true;
        }
        return false;
    }

    public static void appendExtraCasterTriangles(Object level, int x, int y, int z, Object blockState,
                                                  List<ForwardLight.CasterTri> out) {
        for (var ov : CASTER_OVERRIDES.values()) {
            ov.appendExtraTriangles(level, x, y, z, blockState, out);
        }
    }

    public static RenderBridge bridge() {
        return demo != null ? demo.engine() : RenderBridge.ABSENT;
    }

    public static void presentAll(long frameId, float partialTick) {
        if (demo == null) return;
        var poses = new ArrayList<GroupPose>();
        var deforms = new ArrayList<ProceduralDeform>();
        var lights = new ArrayList<LightState>();
        demo.appendTo(poses, deforms, lights);
        for (var provider : HOST_PROVIDERS.values()) {
            provider.collect(frameId, partialTick, poses, deforms, lights);
        }
        demo.engine().present(new PresentationFrame(frameId, partialTick, poses, deforms, lights));
    }

    public static final class FrameTimings {
        private final long[] frameNanos = new long[120];
        private final long[] sorted = new long[frameNanos.length];
        private long lastFrameNs = Long.MIN_VALUE;
        private int next, count;
        public long prepareNs, terrainDrawNs, geomDrawNs, volumeNs, terrainScans;
        private long prepareUs, terrainDrawUs, geomDrawUs, volumeUs;

        public void begin(long now) {
            if (lastFrameNs != Long.MIN_VALUE) {
                frameNanos[next] = now - lastFrameNs;
                next = (next + 1) % frameNanos.length;
                if (count < frameNanos.length) count++;
            }
            lastFrameNs = now;
            // Commands can arrive before terrain draws. Report the last completed frame.
            prepareUs = prepareNs / 1000;
            terrainDrawUs = terrainDrawNs / 1000;
            geomDrawUs = geomDrawNs / 1000;
            volumeUs = volumeNs / 1000;
            prepareNs = terrainDrawNs = geomDrawNs = volumeNs = 0;
        }

        String stats() {
            System.arraycopy(frameNanos, 0, sorted, 0, count);
            Arrays.sort(sorted, 0, count);
            // Nearest-rank percentiles over the populated samples, including during warm-up.
            long p50 = count == 0 ? 0 : sorted[(count * 50 - 1) / 100];
            long p95 = count == 0 ? 0 : sorted[(count * 95 - 1) / 100];
            long max = count == 0 ? 0 : sorted[count - 1];
            return String.format(Locale.ROOT, "frame P50/P95/Max=%.1f/%.1f/%.1fms | hexie(us) prep=%d terr=%d geom=%d vol=%d scans=%d",
                    p50 / 1e6, p95 / 1e6, max / 1e6, prepareUs, terrainDrawUs, geomDrawUs, volumeUs, terrainScans);
        }
    }

    /** Both block corners are inclusive. Sodium publishes section coordinates on the render thread. */
    public static void setTrackedBlockBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        minSectionX = minX >> 4;
        minSectionY = minY >> 4;
        minSectionZ = minZ >> 4;
        maxSectionX = maxX >> 4;
        maxSectionY = maxY >> 4;
        maxSectionZ = maxZ >> 4;
        trackingActive = true;
    }

    public static void clearTrackedBounds() {
        trackingActive = false;
        sectionRevision = 0;
        DIRTY_CASTER_SECTIONS.clear();
        DIRTY_VOLUME_SECTIONS.clear();
    }

    public static long packSectionKey(int secX, int secY, int secZ) {
        return (((long) secX & 0x3FFFFFL) << 42) | (((long) secZ & 0x3FFFFFL) << 20) | ((long) secY & 0xFFFFFL);
    }

    public static int unpackSectionX(long key) { return (int) (key >> 42); }
    public static int unpackSectionZ(long key) { return (int) ((key << 22) >> 42); }
    public static int unpackSectionY(long key) { return (int) ((key << 44) >> 44); }

    public static void drainDirtyCasterSections(Set<Long> out) {
        if (DIRTY_CASTER_SECTIONS.isEmpty()) return;
        var it = DIRTY_CASTER_SECTIONS.iterator();
        while (it.hasNext()) {
            out.add(it.next());
            it.remove();
        }
    }

    public static void drainDirtyVolumeSections(Set<Long> out) {
        if (DIRTY_VOLUME_SECTIONS.isEmpty()) return;
        var it = DIRTY_VOLUME_SECTIONS.iterator();
        while (it.hasNext()) {
            out.add(it.next());
            it.remove();
        }
    }

    public static void onSectionPublished(int secX, int secY, int secZ) {
        if (trackingActive && secX >= minSectionX && secX <= maxSectionX
                && secY >= minSectionY && secY <= maxSectionY && secZ >= minSectionZ && secZ <= maxSectionZ) {
            sectionRevision++;
            long key = packSectionKey(secX, secY, secZ);
            DIRTY_CASTER_SECTIONS.add(key);
            DIRTY_VOLUME_SECTIONS.add(key);
        }
    }

    public static HexieRenderEngine engine() { return demo.engine(); }
    public static RendererBackend.Lighting lighting() {
        return demo==null?RendererBackend.Lighting.OFF:engine().lighting();
    }
    public static void save() {
        try { engine().settings().save(config); } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    public static String stats() {
        var e=engine();
        var l=e.lighting();
        var o=e.ownership();
        String lightingOwner=switch(e.route().tier()) { case IRIS -> "shader pack"; case BASIC -> "vanilla"; case FULL -> "HeXie"; };
        return e.route().tier()+" / "+e.settings().preset()+" / "+reason+" / "+pack+
                " | lighting owner="+lightingOwner+
                " | hosts="+HOST_PROVIDERS.size()+" groups="+e.sectionCount()+
                " | lights="+l.lights()+"/"+demo.lights()+" faces="+l.faces()+" rays="+l.rays()+
                " | geometry="+o.submissionsByOwner()+" missing="+o.missing()+" repeats prevented="+o.duplicatesPrevented()+
                " | terrain="+terrainBatches+" ships="+shipBatches+" sections rev="+sectionRevision+
                " MRT changes="+wrapperMismatches+" | "+timings.stats();
    }
}
