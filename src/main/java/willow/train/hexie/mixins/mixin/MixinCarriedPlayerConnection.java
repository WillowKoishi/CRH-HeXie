package willow.train.hexie.mixins.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.compat.create.CarriagePlayerSupport;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;
import willow.train.hexie.network.s2c.CarriagePositionCorrectionPacket;
import willow.train.hexie.vehicle.player.CarriedPlayerConnection;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinCarriedPlayerConnection implements CarriedPlayerConnection {
    @Shadow public ServerPlayer player;
    @Shadow private double firstGoodX;
    @Shadow private double firstGoodY;
    @Shadow private double firstGoodZ;
    @Shadow private double lastGoodX;
    @Shadow private double lastGoodY;
    @Shadow private double lastGoodZ;
    @Shadow private Vec3 awaitingPositionFromClient;
    @Shadow private int aboveGroundTickCount;
    @Shadow private boolean clientIsFloating;
    @Unique private UUID hexie$correctionCarriage;

    @Inject(method = "teleport(DDDFFLjava/util/Set;)V", at = @At("HEAD"))
    private void hexie$identifyCorrection(double x, double y, double z, float yaw, float pitch,
            Set<RelativeMovement> relative, CallbackInfo ci) {
        hexie$correctionCarriage = null;
        if (!relative.isEmpty() || player.isChangingDimension()
                || player.position().distanceToSqr(x, y, z) > 1e-12) return;
        SnapshotCarriageEntity car = CarriagePlayerSupport.carriage(player);
        if (car != null) hexie$correctionCarriage = car.getUUID();
    }

    @WrapOperation(method = "teleport(DDDFFLjava/util/Set;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void hexie$sendCorrection(ServerGamePacketListenerImpl connection, Packet<?> packet, Operation<Void> send) {
        if (hexie$correctionCarriage != null && packet instanceof ClientboundPlayerPositionPacket correction) {
            SnapshotCarriageEntity car = CarriagePlayerSupport.carriage(player);
            if (car != null && car.getUUID().equals(hexie$correctionCarriage)) {
                var message = new CarriagePositionCorrectionPacket(car.getId(), car.getUUID(),
                        CarriagePlayerSupport.localPosition(player), correction);
                send.call(connection, new ClientboundCustomPayloadPacket(message));
                return;
            }
        }
        send.call(connection, packet);
    }

    @Override public void hexie$grounded() { aboveGroundTickCount = 0; clientIsFloating = false; }

    @Override public boolean hexie$canCarry() { return awaitingPositionFromClient == null; }

    @Override public boolean hexie$isCorrectingOn(UUID carriage) {
        return awaitingPositionFromClient != null && carriage.equals(hexie$correctionCarriage);
    }

    @Override public void hexie$carry(Vec3 displacement) {
        // Vanilla confirms the pending destination when the acknowledgement arrives. The
        // destination of a correction on the carriage moves with the player's feet.
        if (awaitingPositionFromClient != null && hexie$correctionCarriage != null)
            awaitingPositionFromClient = awaitingPositionFromClient.add(displacement);
        firstGoodX += displacement.x; firstGoodY += displacement.y; firstGoodZ += displacement.z;
        lastGoodX += displacement.x; lastGoodY += displacement.y; lastGoodZ += displacement.z;
    }
}
