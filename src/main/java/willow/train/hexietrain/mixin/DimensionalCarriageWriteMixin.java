package willow.train.hexietrain.mixin;

import com.simibubi.create.content.trains.entity.Carriage;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import willow.train.hexietrain.TrainBinder;

/**
 * 跳过影子车厢"储物管理"的序列化。
 *
 * <p>{@code Carriage.DimensionalCarriageEntity.write()} 会调用
 * {@code storage.write(...)}，而 {@code storage} 背后的
 * {@code MountedStorageManager} 只有在车厢有 contraption 时才会初始化。
 *
 * <p>影子车没有 contraption → 世界自动存档时直接抛
 * {@code IllegalStateException: MountedStorageManager is uninitialized}。
 *
 * <p>我们不需要序列化影子车厢的储物，写个空标签即可。
 */
@Mixin(targets = "com.simibubi.create.content.trains.entity.Carriage$DimensionalCarriageEntity")
public abstract class DimensionalCarriageWriteMixin {

    @Shadow @Final Carriage this$0;

    @Inject(method = "write", at = @At("HEAD"), cancellable = true, require = 0)
    private void hexie$skipStorageWrite(HolderLookup.Provider registries,
                                        CallbackInfoReturnable<CompoundTag> cir) {
        try {
            if (TrainBinder.isOurCarriage(this.this$0)) {
                cir.setReturnValue(new CompoundTag());
            }
        } catch (Throwable ignored) {
        }
    }
}