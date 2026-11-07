package willow.train.hexie.mixins.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.sync.ContraptionSeatMappingPacket;
import com.simibubi.create.content.trains.entity.CarriageContraption;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.compat.create.TrainSeatDismount;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;
import willow.train.hexie.client.events.CarriageStandingClient;

/** Capture the local seat exit before Create replaces its mapping, then hand it to client following. */
@Mixin(value = ContraptionSeatMappingPacket.class, remap = false)
public abstract class MixinDrivingConsoleDismountSync {
    @WrapOperation(method = "handle", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;getPassengerPosition(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/world/phys/Vec3;"),
            remap = false)
    private Vec3 hexie$operationPointExit(AbstractContraptionEntity vehicle, Entity passenger,
                                         float partialTicks, Operation<Vec3> original) {
        // The packet reaches this call only for the local dismounting player,
        // before it replaces the old seat mapping with the post-dismount mapping.
        if (vehicle instanceof SnapshotCarriageEntity car && passenger instanceof Player player) {
            var contraption = car.getContraption();
            var seat = contraption == null ? null : contraption.getSeatOf(player.getUUID());
            Vec3 local = seat == null ? null : TrainSeatDismount.localBehind(car, seat);
            if (local == null) {
                Vec3 nativeExit = original.call(vehicle, passenger, partialTicks);
                // The native exit uses toGlobalVector's anchor, not the render-adjusted
                // standing frame. Invert that same transform before the body moves again.
                if (nativeExit != null) local = car.toLocalVector(nativeExit, partialTicks);
            }
            CarriageStandingClient.dismount(player, car, local);
            // No stale world exit may later overwrite the client-resolved local feet.
            return null;
        }
        if (vehicle.getContraption() instanceof CarriageContraption contraption) {
            var seat = contraption.getSeatOf(passenger.getUUID());
            if (seat != null) {
                Vec3 exit = TrainSeatDismount.behind(vehicle, seat);
                if (exit != null) return exit;
            }
        }
        return original.call(vehicle, passenger, partialTicks);
    }
}
