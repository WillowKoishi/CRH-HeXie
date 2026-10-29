package willow.train.hexie.mixins.mixin;

import com.simibubi.create.content.trains.track.TrackBlockEntity;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.blocks.track.standard.BallastData;
import java.util.*;

@Mixin(value = TrackBlockEntity.class, remap = false)
public abstract class MixinTrackBallastData implements BallastData {
    @Unique private BlockState hexie$ballast = Blocks.COBBLESTONE.defaultBlockState();
    @Unique private final Map<BlockPos, BlockState> hexie$curves = new HashMap<>();
    public BlockState hexie$getBallast(BlockPos target) {
        return target == null ? hexie$ballast : hexie$curves.getOrDefault(target, Blocks.COBBLESTONE.defaultBlockState());
    }
    public void hexie$setBallast(BlockPos target, BlockState state) {
        if (target == null) hexie$ballast = state;
        else hexie$curves.put(target.immutable(), state);
        ((TrackBlockEntity) (Object) this).notifyUpdate();
    }
    @Inject(method = "write", at = @At("RETURN"), require = 1)
    private void hexie$write(CompoundTag tag, HolderLookup.Provider registries, boolean packet, CallbackInfo ci) {
        if (!hexie$ballast.is(Blocks.COBBLESTONE)) tag.put("HexieBallast", NbtUtils.writeBlockState(hexie$ballast));
        CompoundTag curves = new CompoundTag();
        // Relative keys survive schematic relocation together with Create's relative connections.
        BlockPos origin = ((TrackBlockEntity) (Object) this).getBlockPos();
        hexie$curves.forEach((pos, state) -> curves.put(Long.toString(pos.subtract(origin).asLong()), NbtUtils.writeBlockState(state)));
        if (!curves.isEmpty()) tag.put("HexieCurveBallastRelative", curves);
    }
    @Inject(method = "read", at = @At("RETURN"), require = 1)
    private void hexie$read(CompoundTag tag, HolderLookup.Provider registries, boolean packet, CallbackInfo ci) {
        hexie$ballast = tag.contains("HexieBallast") ? NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("HexieBallast")) : Blocks.COBBLESTONE.defaultBlockState();
        hexie$curves.clear();
        BlockPos origin = ((TrackBlockEntity) (Object) this).getBlockPos();
        boolean relative = tag.contains("HexieCurveBallastRelative");
        CompoundTag curves = tag.getCompound(relative ? "HexieCurveBallastRelative" : "HexieCurveBallast");
        for (String key : curves.getAllKeys()) try {
            BlockPos pos = BlockPos.of(Long.parseLong(key));
            hexie$curves.put(relative ? origin.offset(pos) : pos, NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), curves.getCompound(key)));
        } catch (NumberFormatException ignored) { }
    }
    @Inject(method = "removeConnection", at = @At("HEAD"), require = 1)
    private void hexie$removeTexture(BlockPos target, CallbackInfo ci) { hexie$curves.remove(target); }

    @Inject(method = "removeConnection", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"), cancellable = true, require = 1)
    private void hexie$keepPaintedTrack(BlockPos target, CallbackInfo ci) {
        if (!hexie$ballast.is(Blocks.COBBLESTONE)) ci.cancel();
    }
}
