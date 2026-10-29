package willow.train.hexie.mixins.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import willow.train.hexie.blocks.bogeys.HexieBogeyBlock;
import willow.train.hexie.compat.create.CreateAssemblyStationAccess;
import willow.train.hexie.compat.create.CreateStationAssemblyRequest;
import willow.train.hexie.compat.create.CreateVehicleAssembly;
import willow.train.hexie.compat.create.CompatibilityTrain;
import willow.train.hexie.vehicle.assembly.AssemblyRegion;
import willow.train.hexie.vehicle.assembly.CarriageSnapshotStore;

/** Uses the existing station action and rail scan; HeXie owns the subsequent structure lifecycle. */
@Mixin(value = StationBlockEntity.class, remap = false)
public abstract class MixinVehicleAssemblyStation extends SmartBlockEntity implements CreateAssemblyStationAccess {
    @Shadow int bogeyCount;
    @Shadow int[] bogeyLocations;
    @Shadow AbstractBogeyBlock<?>[] bogeyTypes;
    @Shadow boolean[] upsideDownBogeys;
    @Shadow protected AssemblyException lastException;
    @Shadow protected int failedCarriageIndex;
    @Unique private UUID hexie$assemblyId;

    protected MixinVehicleAssemblyStation(BlockEntityType<?> type, BlockPos position, BlockState state) {
        super(type, position, state);
    }

    @Override
    public void initialize() {
        super.initialize();
        if (getLevel() instanceof ServerLevel level)
            hexie$setAssemblyId(CarriageSnapshotStore.get(level).activeAt(getBlockPos())
                    .map(CarriageSnapshotStore.ActiveAssembly::id).orElse(null));
    }

    @Override
    public @Nullable UUID hexie$getAssemblyId() { return hexie$assemblyId; }

    @Override
    public boolean hexie$hasAssemblyFailure() { return lastException != null; }

    @Override
    public void hexie$setAssemblyFailure(@Nullable Exception failure) {
        lastException = failure == null ? null : failure instanceof AssemblyException assembly ? assembly
                : new AssemblyException(Component.literal(failure.getMessage() == null
                        ? failure.getClass().getSimpleName() : failure.getMessage()));
        failedCarriageIndex = -1;
        hexie$station().notifyUpdate();
    }

    @Override
    public void hexie$setAssemblyId(@Nullable UUID id) {
        if (Objects.equals(hexie$assemblyId, id)) return;
        hexie$assemblyId = id;
        hexie$station().notifyUpdate();
    }

    @Override
    public Optional<List<BlockPos>> hexie$getAssemblySupports() {
        if (bogeyCount <= 0 || bogeyLocations == null || bogeyTypes == null || upsideDownBogeys == null)
            return Optional.empty();
        var station = hexie$station();
        var trackPosition = station.edgePoint.getGlobalPosition();
        var offset = BlockPos.containing(station.edgePoint.getTrack().getUpNormal(
                station.getLevel(), trackPosition, station.edgePoint.getTrackBlockState()));
        var result = new ArrayList<BlockPos>(bogeyCount);
        for (int i = 0; i < bogeyCount; i++) {
            if (!(bogeyTypes[i] instanceof HexieBogeyBlock<?>)) return Optional.empty();
            var bogeyOffset = upsideDownBogeys[i]
                    ? new BlockPos(offset.getX(), -offset.getY(), offset.getZ()) : offset;
            result.add(trackPosition.relative(station.getAssemblyDirection(), bogeyLocations[i] + 1).offset(bogeyOffset));
        }
        return Optional.of(List.copyOf(result));
    }

    @Inject(method = "assemble", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/trains/station/StationBlockEntity;refreshAssemblyInfo()V",
            shift = At.Shift.AFTER), cancellable = true)
    private void hexie$assemble(UUID owner, CallbackInfo ci) {
        var station = hexie$station();
        if (!(station.getLevel() instanceof ServerLevel level)) return;
        try {
            // Connectivity, not an invented radius, determines the body selected by the scanner.
            var region = new AssemblyRegion.Box(-Level.MAX_LEVEL_SIZE, level.getMinBuildHeight(), -Level.MAX_LEVEL_SIZE,
                    Level.MAX_LEVEL_SIZE - 1, level.getMaxBuildHeight() - 1, Level.MAX_LEVEL_SIZE - 1);
            var request = CreateStationAssemblyRequest.fromRefreshed(station, region);
            if (request.isEmpty()) return;
            ci.cancel();
            UUID assembly = CreateVehicleAssembly.assemble(station, owner, request.get());
            hexie$setAssemblyId(assembly);
            station.exitAssemblyMode();
        } catch (Exception failure) {
            ci.cancel();
            hexie$showFailure(failure);
        }
    }

    @Inject(method = "enterAssemblyMode", at = @At("HEAD"), cancellable = true)
    private void hexie$restoreBeforeEntering(@Nullable ServerPlayer sender, CallbackInfoReturnable<Boolean> cir) {
        var station = hexie$station();
        // Create ignores tryDisassembleTrain's failure here; our failed restore must retain the assembled state.
        var current = station.getStation();
        if (current != null && current.getPresentTrain() instanceof CompatibilityTrain
                && !station.isAssembling() && !station.tryDisassembleTrain(sender))
            cir.setReturnValue(false);
    }

    @Inject(method = "write", at = @At("RETURN"))
    private void hexie$write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        if (hexie$assemblyId != null) tag.putUUID("HeXieAssembly", hexie$assemblyId);
        else tag.remove("HeXieAssembly");
    }

    @Inject(method = "read", at = @At("RETURN"))
    private void hexie$read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        hexie$assemblyId = tag.hasUUID("HeXieAssembly") ? tag.getUUID("HeXieAssembly") : null;
    }

    @Unique
    private void hexie$showFailure(Exception failure) {
        hexie$setAssemblyFailure(failure);
    }

    @Unique
    private StationBlockEntity hexie$station() { return (StationBlockEntity) (Object) this; }
}
