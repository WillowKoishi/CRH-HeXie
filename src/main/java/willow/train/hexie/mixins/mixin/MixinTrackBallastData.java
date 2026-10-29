package willow.train.hexie.mixins.mixin;

import com.simibubi.create.content.trains.track.TrackBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.blocks.track.standard.BallastData;
import willow.train.hexie.blocks.track.standard.TrackBallast;
import java.util.HashMap;
import java.util.Map;

@Mixin(value = TrackBlockEntity.class, remap = false)
public abstract class MixinTrackBallastData implements BallastData {
    @Unique private BlockState hexie$ballast = Blocks.COBBLESTONE.defaultBlockState();
    @Unique private final Map<BlockPos, BlockState> hexie$curveBallast = new HashMap<>();

    @Override public BlockState hexie$getBallast(BlockPos target) {
        return target == null ? hexie$ballast : hexie$curveBallast.getOrDefault(target, Blocks.COBBLESTONE.defaultBlockState());
    }

    @Override public void hexie$setBallast(BlockPos target, BlockState state) {
        if (target == null) hexie$ballast = state;
        else hexie$curveBallast.put(target.immutable(), state);
        ((TrackBlockEntity) (Object) this).notifyUpdate();
    }

    @Inject(method = "write", at = @At("RETURN"), require = 1)
    private void hexie$write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        tag.put("HexieBallast", NbtUtils.writeBlockState(hexie$ballast));
        CompoundTag curves = new CompoundTag();
        hexie$curveBallast.forEach((pos, state) -> curves.put(Long.toString(pos.asLong()), NbtUtils.writeBlockState(state)));
        tag.put("HexieCurveBallast", curves);
    }

    @Inject(method = "read", at = @At("RETURN"), require = 1)
    private void hexie$read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        hexie$ballast = tag.contains("HexieBallast")
                ? NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("HexieBallast"))
                : Blocks.COBBLESTONE.defaultBlockState();
        hexie$curveBallast.clear();
        CompoundTag curves = tag.getCompound("HexieCurveBallast");
        for (String key : curves.getAllKeys()) {
            try {
                hexie$curveBallast.put(BlockPos.of(Long.parseLong(key)),
                        NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), curves.getCompound(key)));
            } catch (NumberFormatException ignored) { }
        }
    }

    @Inject(method = "removeConnection", at = @At("HEAD"), cancellable = true, require = 1)
    private void hexie$keepStraightBallast(BlockPos target, CallbackInfo ci) {
        TrackBlockEntity track = (TrackBlockEntity) (Object) this;
        hexie$curveBallast.remove(target);
        if (!TrackBallast.enabled(track.getBlockState())) return;
        // Create normally deletes an empty track BE. Standard track still needs its ballast data/rendering.
        if (track.isTilted()) track.tilt.captureSmoothingHandles();
        var removed = track.getConnections().remove(target);
        track.notifyUpdate();
        if (removed != null) track.manageFakeTracksAlong(removed, true);
        ci.cancel();
    }
}
