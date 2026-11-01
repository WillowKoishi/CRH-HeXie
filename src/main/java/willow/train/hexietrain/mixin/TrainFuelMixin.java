package willow.train.hexietrain.mixin;

import com.simibubi.create.content.trains.entity.Train;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexietrain.TrainBinder;

/**
 * 跳过 Create 的"烧燃料"逻辑。
 *
 * <p>{@code Train.burnFuel()} 会读 {@code carriage.storage.getFuelItems()}，
 * 而 {@code storage} 只有在车厢有 contraption 时才初始化。
 * 影子车的 storage 是 null → 直接抛
 * {@code IllegalStateException: MountedStorageManager is uninitialized}。
 *
 * <p>我们的能量由自己的 {@code SpeedModel} / 能量账本负责，不需要 Create 的燃料系统。
 */
@Mixin(Train.class)
public abstract class TrainFuelMixin {

    @Inject(method = "burnFuel", at = @At("HEAD"), cancellable = true, require = 0)
    private void hexie$skipFuelBurn(CallbackInfo ci) {
        try {
            Train self = (Train) (Object) this;
            if (TrainBinder.OUR_TRAINS.contains(self.id)) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
        }
    }
}