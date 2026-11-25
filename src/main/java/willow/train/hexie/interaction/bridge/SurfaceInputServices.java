package willow.train.hexie.interaction.bridge;

import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.interaction.SurfaceInputBridge;
import willow.train.hexie.core.api.interaction.BlockControlContext;
import willow.train.hexie.interaction.surface.BlockSurfaceSource;
import willow.train.hexie.interaction.surface.CarriageBlockPicker;
import willow.train.hexie.interaction.surface.SurfaceInputSources;

final class SurfaceInputServices implements SurfaceInputBridge,
        SurfaceInputBridge.SurfaceInputSourcesOperations {
    @Override
    public SurfaceInputSourcesRayView localRay(BlockControlContext target, Vec3 from, Vec3 to) {
        return SurfaceInputSources.frame(target).toLocal(from, to);
    }

    @Override
    public BlockHitResult pickCarriageBlock(BlockGetter blocks, Iterable<BlockPos> owners, SurfaceInputSourcesRayView ray) {
        return CarriageBlockPicker.pick(blocks, owners, ray);
    }

    @Override
    public BlockHitResult pickSurface(BlockControlContext target, SurfaceInputSourcesRayView localRay) {
        return BlockSurfaceSource.pick(target, localRay);
    }

    @Override
    public SurfaceInputSourcesOperations surfaceInputSources() {
        return this;
    }

    @Override
    public SurfaceInputSourcesRayView createSurfaceInputSourcesRay(Vec3 from, Vec3 to) {
        return new SurfaceInputSources.Ray(from, to);
    }

    @Override
    public void registerFrame(Function<BlockControlContext, SurfaceInputSourcesFrameView> provider) {
        SurfaceInputSources.registerFrame(provider);
    }

    @Override
    public void registerBlocker(BooleanSupplier blocker) {
        SurfaceInputSources.registerBlocker(blocker);
    }

    @Override
    public void registerEye(BiFunction<Player, Float, Vec3> provider) {
        SurfaceInputSources.registerEye(provider);
    }

    @Override
    public void registerEyeOffset(BiFunction<Player, Float, Vec3> provider) {
        SurfaceInputSources.registerEyeOffset(provider);
    }

    @Override
    public float partialTick() {
        return SurfaceInputSources.partialTick();
    }

    @Override
    public Set<BlockPos> cells(Vec3 from, Vec3 to) {
        return SurfaceInputSources.cells(from, to);
    }
}
