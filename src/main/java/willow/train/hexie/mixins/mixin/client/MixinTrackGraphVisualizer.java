package willow.train.hexie.mixins.mixin.client;

import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphVisualizer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import willow.train.hexie.init.track.TrackMaterialInit;

@Mixin(value = TrackGraphVisualizer.class, remap = false)
public abstract class MixinTrackGraphVisualizer {
    // Offset only the visual samples, never the underlying graph or signal boundaries.
    @Redirect(method = "visualiseSignalEdgeGroups", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/trains/graph/TrackEdge;getPosition(Lcom/simibubi/create/content/trains/graph/TrackGraph;D)Lnet/minecraft/world/phys/Vec3;"),
            require = 1)
    private static Vec3 hexie$raiseSignalSection(TrackEdge edge, TrackGraph graph, double position) {
        Vec3 point = edge.getPosition(graph, position);
        return edge.getTrackMaterial().trackType == TrackMaterialInit.HEXIE_STANDARD
                ? point.add(0, TrackMaterialInit.RAIL_HEIGHT_INCREASE, 0)
                : point;
    }
}
