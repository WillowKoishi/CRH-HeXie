package willow.train.hexie.interaction.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.interaction.InteractionBridge;
import willow.train.hexie.core.api.cab.DrivingConsoleContext;
import willow.train.hexie.core.api.interaction.BlockControlContext;
import willow.train.hexie.interaction.common.ControlInputTargets;
import willow.train.hexie.interaction.common.NativeControlSurface;

final class InteractionServices implements InteractionBridge,
        InteractionBridge.ControlInputTargetsOperations,
        InteractionBridge.NativeControlSurfaceControlOperations {
    private final NativeControlSurface nativeSurfaces = new NativeControlSurface();

    @Override
    public ControlInputTargetsOperations controlInputTargets() {
        return this;
    }

    @Override
    public NativeControlSurfaceControlOperations nativeControlSurfaceControl() {
        return this;
    }

    @Override
    public NativeControlSurfaceOperations nativeControlSurface() {
        return nativeSurfaces;
    }

    @Override
    public void registerAdapter(ControlInputTargetsAdapterView adapter) {
        ControlInputTargets.registerAdapter(adapter);
    }

    @Override
    public void registerCoordinates(ControlInputTargetsCoordinatesView coordinates) {
        ControlInputTargets.registerCoordinates(coordinates);
    }

    @Override
    public BlockControlContext seated(DrivingConsoleContext cab, float partialTick) {
        return ControlInputTargets.seated(cab, partialTick);
    }

    @Override
    public BlockControlContext world(Player player, int vehicleId, BlockPos position, float partialTick) {
        return ControlInputTargets.world(player, vehicleId, position, partialTick);
    }

    @Override
    public Vec3 toWorld(BlockControlContext target, Player player, Vec3 local) {
        return ControlInputTargets.toWorld(target, player, local);
    }

    @Override
    public Vec3 toLocal(BlockControlContext target, Player player, Vec3 world) {
        return ControlInputTargets.toLocal(target, player, world);
    }

    @Override
    public Vec3 eye(BlockControlContext target, Player player) {
        return ControlInputTargets.eye(target, player);
    }

    @Override
    public Object container(BlockControlContext target) {
        return ControlInputTargets.container(target);
    }

    @Override
    public BlockEntity blockEntity(BlockControlContext target) {
        return ControlInputTargets.blockEntity(target);
    }

    @Override
    public boolean canInteract(BlockControlContext target, Player player, Vec3 localPoint) {
        return ControlInputTargets.canInteract(target, player, localPoint);
    }

    @Override
    public boolean seatDiscoveryLoaded(Player player, Entity vehicle, BlockPos console) {
        return ControlInputTargets.seatDiscoveryLoaded(player, vehicle, console);
    }

    @Override
    public NativeControlSurfaceControlView door() {
        return NativeControlSurface.Control.DOOR;
    }

    @Override
    public NativeControlSurfaceControlView couplerHead() {
        return NativeControlSurface.Control.COUPLER_HEAD;
    }

    @Override
    public NativeControlSurfaceControlView couplerBody() {
        return NativeControlSurface.Control.COUPLER_BODY;
    }
}
