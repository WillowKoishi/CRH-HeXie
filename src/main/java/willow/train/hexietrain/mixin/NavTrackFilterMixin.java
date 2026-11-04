package willow.train.hexietrain.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.simibubi.create.content.trains.entity.Navigation;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.track.TrackMaterial;

import willow.train.hexietrain.TrainBinder;

/**
 * 寻路时跳过轨道材质过滤。
 *
 * <p>Create 的 Navigation.search 只允许转向架声明过的材质类型：
 * <pre>if (!validTypes.contains(edge.getTrackMaterial().trackType)) continue;</pre>
 * 我们用的是 Create 的 SmallBogey（create:standard），而轨道是 hexie:standard，
 * 于是每一条边都被跳过，findPathTo 必然返回 null。
 */
@Mixin(Navigation.class)
public abstract class NavTrackFilterMixin {

    @Redirect(method = "search(DDZLjava/util/ArrayList;Lcom/simibubi/create/content/trains/entity/Navigation$StationTest;)V",
              at = @At(value = "INVOKE", target = "Ljava/util/Set;contains(Ljava/lang/Object;)Z"))
    private boolean hexie$allowAnyTrackType(Set<TrackMaterial.TrackType> set, Object probe) {
        try {
            Train t = ((Navigation) (Object) this).train;
            if (t != null && TrainBinder.OUR_TRAINS.contains(t.id)) return true;
        } catch (Throwable ignored) {
        }
        return set.contains(probe);
    }
}