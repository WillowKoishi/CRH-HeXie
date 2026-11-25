package willow.train.hexie.bridge.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.api.cab.DrivingConsoleContext;
import willow.train.hexie.core.api.interaction.BlockControl;
import willow.train.hexie.core.api.interaction.BlockControlContext;
import willow.train.hexie.core.api.interaction.BlockControlHit;
import willow.train.hexie.core.api.interaction.BlockControlHost;
import willow.train.hexie.core.api.interaction.ControlSurface;

public interface InteractionBridge {
    /** Pick in storage coordinates, including host overrides for animated doors and couplers. */
    default BlockControlHit pickControl(BlockControlHost host, BlockControlContext target, Vec3 from, Vec3 to) {
        return host.pickControl(target, from, to);
    }

    ControlInputTargetsOperations controlInputTargets();

    interface ControlInputTargetsOperations {
        void registerAdapter(ControlInputTargetsAdapterView adapter);
        void registerCoordinates(ControlInputTargetsCoordinatesView coordinates);
        BlockControlContext seated(DrivingConsoleContext cab, float partialTick);
        BlockControlContext world(Player player, int vehicleId, BlockPos position, float partialTick);
        Vec3 toWorld(BlockControlContext target, Player player, Vec3 local);
        Vec3 toLocal(BlockControlContext target, Player player, Vec3 world);
        Vec3 eye(BlockControlContext target, Player player);
        Object container(BlockControlContext target);
        BlockEntity blockEntity(BlockControlContext target);
        boolean canInteract(BlockControlContext target, Player player, Vec3 localPoint);
        boolean seatDiscoveryLoaded(Player player, Entity vehicle, BlockPos console);
    }

    NativeControlSurfaceControlOperations nativeControlSurfaceControl();

    interface NativeControlSurfaceControlOperations {
        NativeControlSurfaceControlView door();
        NativeControlSurfaceControlView couplerHead();
        NativeControlSurfaceControlView couplerBody();
    }

    NativeControlSurfaceOperations nativeControlSurface();

    interface NativeControlSurfaceOperations {
        ControlSurface door();
        ControlSurface surfaceGeometry(NativeControlSurfaceView surface, BlockState state);
        AABB controlBounds(NativeControlSurfaceView surface, BlockState state);
        AABB controlBounds(NativeControlSurfaceView surface, BlockState state, BlockEntity entity);
        BlockControlHit pickControl(NativeControlSurfaceView surface, BlockControlContext target, Vec3 from,
                Vec3 to);
    }

    interface ControlInputTargetsAdapterView {
        boolean supports(Entity carrier);
        BlockControlContext world(Entity carrier, BlockPos position, float partialTick);
        Object container(BlockControlContext target);
        BlockEntity blockEntity(BlockControlContext target);
        Vec3 toStorage(BlockControlContext target, Vec3 local);
        Vec3 toLocal(BlockControlContext target, Vec3 storage);
        boolean canInteract(BlockControlContext target, Player player, Vec3 localPoint);
        boolean seatDiscoveryLoaded(Player player, Entity vehicle, BlockPos console);
    }

    interface ControlInputTargetsCoordinatesView {
        default boolean supports(BlockControlContext target, Player player) { return true; }
        default Vec3 toWorld(BlockControlContext target, Player player, Vec3 storage) { return storage; }
        default Vec3 toStorage(BlockControlContext target, Player player, Vec3 world) { return world; }
        default Vec3 eye(BlockControlContext target, Player player) { return player.getEyePosition(); }
        default boolean supportsBlock(Level level, BlockPos owner) { return false; }
        default Vec3 blockToWorld(Level level, BlockPos owner, Vec3 storage) { return storage; }
    }

    interface NativeControlSurfaceView extends BlockControlHost {
        default ControlSurface surfaceGeometry(BlockState state) {
            return HexieBridges.interaction().nativeControlSurface().surfaceGeometry(this, state);
        }
        default boolean nativeInteraction() { return true; }
        default AABB controlBounds(BlockState state) {
            return HexieBridges.interaction().nativeControlSurface().controlBounds(this, state);
        }
        default AABB controlBounds(BlockState state, BlockEntity entity) {
            return HexieBridges.interaction().nativeControlSurface().controlBounds(this, state, entity);
        }
        default BlockControlHit pickControl(BlockControlContext target, Vec3 from, Vec3 to) {
            return HexieBridges.interaction().nativeControlSurface().pickControl(this, target, from, to);
        }
    }

    interface NativeControlSurfaceControlView extends BlockControl.Button {}
}
