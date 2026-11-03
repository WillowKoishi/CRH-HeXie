package willow.train.hexie.mixins.mixin;

import java.util.List;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Navigation;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackNode;
import net.createmod.catnip.data.Couple;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.compat.create.CreateWaypointSupport;

/** Supplies an arbitrary observation origin to native search, only for disposable preview instances. */
@Mixin(value = Navigation.class, remap = false)
public abstract class MixinNavigationPreview implements CreateWaypointSupport.PathAccess {
    @Override @Accessor("currentPath")
    public abstract void hexie$previewPath(List<Couple<TrackNode>> path);

    @WrapOperation(method = {
            "findPathTo(Ljava/util/ArrayList;D)Lcom/simibubi/create/content/trains/graph/DiscoveredPath;",
            "search(DDZLjava/util/ArrayList;Lcom/simibubi/create/content/trains/entity/Navigation$StationTest;)V"},
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/trains/entity/Carriage;getLeadingPoint()Lcom/simibubi/create/content/trains/entity/TravellingPoint;"))
    private TravellingPoint hexie$previewLeading(Carriage carriage, Operation<TravellingPoint> original) {
        var point = CreateWaypointSupport.previewPoint((Navigation) (Object) this);
        return point == null ? original.call(carriage) : point;
    }

    @WrapOperation(method = {
            "findPathTo(Ljava/util/ArrayList;D)Lcom/simibubi/create/content/trains/graph/DiscoveredPath;",
            "search(DDZLjava/util/ArrayList;Lcom/simibubi/create/content/trains/entity/Navigation$StationTest;)V"},
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/trains/entity/Carriage;getTrailingPoint()Lcom/simibubi/create/content/trains/entity/TravellingPoint;"))
    private TravellingPoint hexie$previewTrailing(Carriage carriage, Operation<TravellingPoint> original) {
        var point = CreateWaypointSupport.previewPoint((Navigation) (Object) this);
        return point == null ? original.call(carriage) : point;
    }
}
