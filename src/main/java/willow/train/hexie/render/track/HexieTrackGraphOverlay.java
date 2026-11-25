package willow.train.hexie.render.track;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.KineticDebugger;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.outliner.LineOutline;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.railway.TrackRegistryBridge;

/** Translates Create's own signal lines, retaining its samples, boundary gaps, colors and fade. */
public final class HexieTrackGraphOverlay {
    private HexieTrackGraphOverlay() {}

    public static void afterTick(ClientTickEvent.Post event) {
        var outliner = Outliner.getInstance();
        boolean debug = KineticDebugger.isF3DebugModeActive() && AllConfigs.client().showTrackGraphOnF3.get();
        outliner.getOutlines().forEach((slot, entry) -> {
            // Ponder ages entries in Pre. Create refreshes visible lines to one tick in Post.
            if (entry.getTicksTillRemoval() != 1 || !(entry.getOutline() instanceof LineOutline line)) return;
            if (line instanceof TrackLine existing) {
                // F3 graph lines can reuse the same slot. Fading signal sections retain their last height.
                existing.signal = !debug;
                return;
            }
            if (debug) return;
            TrackEdge edge = edgeOf(slot);
            if (edge == null) return;
            var material = edge.getTrackMaterial();
            if (material.trackType != HexieBridges.createTrack().hexieStandard()) return;
            var backend = HexieBridges.createTrain().createRailwayBackend();
            double height = backend.supports(material) ? backend.heightIncrease(material)
                    : TrackRegistryBridge.RAIL_HEIGHT_INCREASE;
            outliner.showOutline(slot, new TrackLine(line, height));
        });
    }

    private static TrackEdge edgeOf(Object slot) {
        if (slot instanceof TrackEdge edge) return edge;
        if (slot instanceof Pair<?, ?> pair) {
            if (pair.getFirst() instanceof SignalBoundary && pair.getSecond() instanceof TrackEdge edge) return edge;
            if (pair.getFirst() instanceof TrackEdge edge && pair.getSecond() instanceof Vec3) return edge;
        }
        return null;
    }

    private static final class TrackLine extends LineOutline {
        private final LineOutline line;
        private final double height;
        private boolean signal = true;

        TrackLine(LineOutline line, double height) {
            this.line = line;
            this.height = height;
        }

        @Override public LineOutline set(Vec3 start, Vec3 end) {
            line.set(start, end);
            return this;
        }

        @Override public OutlineParams getParams() {
            return line.getParams();
        }

        @Override public void render(PoseStack pose, SuperRenderTypeBuffer buffer, Vec3 camera, float partial) {
            line.render(pose, buffer, signal ? camera.subtract(0, height, 0) : camera, partial);
        }
    }
}
