package dev.hexie.renderer.neoforge;

import dev.hexie.renderer.*;
import dev.hexie.renderer.mc.*;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

/** Connects the NeoForge client to multi-light GPU forward shading (Sodium terrain, Sable ships,
 * and HeXie per-pixel geometry) with zero synchronous GPU-to-CPU stalls in FULL tier. */
final class PrototypeRenderer implements RendererBackend {
    private final MultiLightBackend core = new MultiLightBackend(() ->
            ClientRenderState.engine().route().tier() == RenderSettings.Tier.FULL ? RenderTier.FULL : RenderTier.BASIC);
    private WorldCasters casters = new WorldCasters();
    final WorldLightVolume volume = new WorldLightVolume();

    @Override
    public Lighting prepare(Snapshot snapshot, RenderSettings settings) {
        if (ClientRenderState.engine().route().tier() != RenderSettings.Tier.FULL
                || settings.effective(RenderSettings.Field.ACTIVE_LIGHTS, RenderSettings.Tier.FULL) == 0) {
            ForwardLight.clear();
            return Lighting.OFF;
        }
        int budget = settings.get(RenderSettings.Field.EXACT_RAYS);
        long start = System.nanoTime();
        casters.prepare(Minecraft.getInstance().level, snapshot, volume, settings, ModList.get().isLoaded("sable"), budget);
        ClientRenderState.timings.prepareNs += System.nanoTime() - start;
        int activeLights = ForwardLight.publishedLamps();
        int triFaces = ForwardLight.publishedTriangles();
        return new Lighting() {
            private Lighting lazyCore;

            @Override public int lights() { return activeLights; }
            @Override public int faces() { return triFaces; }
            @Override public int rays() { return ForwardLight.usedRays(); }
            @Override public double energy(long group, int triangleIndex) {
                if (lazyCore == null) lazyCore = core.prepare(snapshot, settings);
                return lazyCore.energy(group, triangleIndex);
            }
            @Override public void drawReceiver(Receiver receiver, Runnable original) {
                ForwardLight.draw(receiver.camera(), receiver.rotation(), receiver.packedNormal(), original);
            }
            @Override public int blockLight(Vec lo, Vec hi) {
                return ForwardLight.blockLight(lo, hi);
            }
            @Override public void close() {
                if (lazyCore != null) {
                    lazyCore.close();
                    lazyCore = null;
                }
            }
        };
    }

    void prepareWorldIfIdle(net.minecraft.client.multiplayer.ClientLevel level, RenderSettings settings) {
        if (ClientRenderState.engine().route().tier() != RenderSettings.Tier.FULL
                || settings.effective(RenderSettings.Field.ACTIVE_LIGHTS, RenderSettings.Tier.FULL) == 0) {
            ForwardLight.clear();
            return;
        }
        int budget = settings.get(RenderSettings.Field.EXACT_RAYS);
        long start = System.nanoTime();
        casters.prepare(level, null, volume, settings, ModList.get().isLoaded("sable"), budget);
        ClientRenderState.timings.prepareNs += System.nanoTime() - start;
    }

    @Override
    public int effective(RenderSettings settings, RenderSettings.Field field, RenderSettings.Tier tier) {
        return core.effective(settings, field, tier);
    }

    @Override public void clear() {
        casters = new WorldCasters();
        volume.dispose();
        ForwardLight.clear();
    }

    void reload() {
        clear();
        ForwardLight.dispose();
        core.close();
    }
}
