package willow.train.hexietrain.mixin;

import com.simibubi.create.content.trains.entity.Carriage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexietrain.TrainBinder;

/**
 * 让"物理列车"的车厢跳过 Create 的 contraption 锚点更新。
 *
 * <p>为什么要这么做：
 * <ul>
 *   <li>{@code Carriage.updateContraptionAnchors()} 会把 {@code positionAnchor} 设上</li>
 *   <li>{@code Carriage.manageEntities()} 只要看到 {@code positionAnchor != null}
 *       就会调 {@code createEntity()}</li>
 *   <li>{@code createEntity()} 拿不到 {@code CarriageContraptionEntity} 就会
 *       {@code train.invalid = true}，下一 tick 列车被移出 RAILWAYS</li>
 * </ul>
 *
 * <p>而我们的车厢本来就不该有 contraption —— 它的实体是 Sable 子结构。
 * 跳过更新后 {@code positionAnchor} 保持 null，整条链路自然不会再触发。
 *
 * <p>锚点由我们在 {@code TrainBinder} 里手动填入 {@code rotationAnchors}，
 * 供其它模组（如电力学的接触网计算）读取。
 */
@Mixin(Carriage.class)
public abstract class CarriageAnchorMixin {

    @Inject(method = "updateContraptionAnchors", at = @At("HEAD"), cancellable = true, require = 0)
    private void hexie$skipAnchorUpdateForPhysicsCarriage(CallbackInfo ci) {
        try {
            Carriage self = (Carriage) (Object) this;
            if (TrainBinder.isOurCarriage(self)) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
            // 绝不因为兼容逻辑把游戏搞崩
        }
    }
}