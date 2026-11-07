package willow.train.hexie.mixins.mixin.client;

import java.util.UUID;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import willow.train.hexie.compat.create.CarriagePlayerSupport;
import willow.train.hexie.network.c2s.CarriagePlayerMovePacket;
import willow.train.hexie.network.c2s.CarriageRestoreAckPacket;
import willow.train.hexie.client.events.CarriageStandingClient;
import willow.train.hexie.client.events.CarriageCamera;

@Mixin(LocalPlayer.class)
public abstract class MixinCarriageLocalPlayer {
    @Unique private Runnable hexie$vanillaMovement;
    @Unique private int hexie$sentCarriageId = -1;
    @Unique private UUID hexie$sentCarriageUuid;
    @Unique private Vec3 hexie$sentLocal;
    @Unique private float hexie$sentYaw, hexie$sentPitch;
    @Unique private boolean hexie$sentGround;
    @Unique private long hexie$movementEpoch;

    @Inject(method = "tick", at = @At("HEAD"))
    private void hexie$alignMovementLook(CallbackInfo ci) {
        CarriageCamera.beforePlayerTick();
    }

    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;hasChunkAt(II)Z"))
    private boolean hexie$tickOnCarriage(boolean loaded) {
        return loaded || CarriagePlayerSupport.attachedCarriage((LocalPlayer) (Object) this) != null;
    }

    @WrapOperation(method = "sendPosition", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void hexie$relativeMovement(ClientPacketListener connection, Packet<?> packet, Operation<Void> original) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (!(packet instanceof ServerboundMovePlayerPacket) || player.isPassenger()) {
            original.call(connection, packet);
            return;
        }
        hexie$vanillaMovement = () -> original.call(connection, packet);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void hexie$queueResolvedMovement(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        Runnable vanillaMovement = hexie$vanillaMovement;
        hexie$vanillaMovement = null;
        // A first contact or departure still needs a report when vanilla sent no movement.
        CarriageStandingClient.afterCollisionMovement(() -> hexie$sendResolvedMovement(player, vanillaMovement));
    }

    @Unique private void hexie$sendResolvedMovement(LocalPlayer player, Runnable vanillaMovement) {
        if (net.minecraft.client.Minecraft.getInstance().player != player) return;
        long epoch = CarriageStandingClient.movementEpoch();
        if (hexie$movementEpoch != epoch) {
            hexie$forgetSupport();
            hexie$movementEpoch = epoch;
        }
        var restore = CarriagePlayerSupport.takeClientRestoreReport(player);
        if (restore != null) {
            // Placement (or carrier removal) completes the handoff before its attached/exit report.
            if (restore.token() != 0) PacketDistributor.sendToServer(new CarriageRestoreAckPacket(restore.token()));
            hexie$sentCarriageId = restore.carriageId();
            hexie$sentCarriageUuid = restore.carriageUuid();
            hexie$sentLocal = null;
        }
        if (player.isPassenger()) {
            hexie$forgetSupport();
            return;
        }
        var car = CarriagePlayerSupport.carriage(player);
        if (car == null) {
            if (hexie$sentCarriageId >= 0) {
                PacketDistributor.sendToServer(new CarriagePlayerMovePacket(hexie$sentCarriageId,
                        hexie$sentCarriageUuid, player.position(), false, player.getYRot(), player.getXRot(), player.onGround()));
                hexie$forgetSupport();
            } else if (vanillaMovement != null) vanillaMovement.run();
            return;
        }
        Vec3 local = CarriagePlayerSupport.localPosition(player);
        boolean changedCar = hexie$sentLocal == null || hexie$sentCarriageId != car.getId()
                || !car.getUUID().equals(hexie$sentCarriageUuid);
        if (!changedCar && vanillaMovement == null && local.distanceToSqr(hexie$sentLocal) <= 1e-8
                && player.getYRot() == hexie$sentYaw && player.getXRot() == hexie$sentPitch
                && player.onGround() == hexie$sentGround) return;
        PacketDistributor.sendToServer(new CarriagePlayerMovePacket(car.getId(), car.getUUID(), local, true,
                player.getYRot(), player.getXRot(),
                player.onGround() && CarriagePlayerSupport.hasFloor(car, player.position(), player.getBbWidth())));
        hexie$sentCarriageId = car.getId();
        hexie$sentCarriageUuid = car.getUUID();
        hexie$sentLocal = local;
        hexie$sentYaw = player.getYRot();
        hexie$sentPitch = player.getXRot();
        hexie$sentGround = player.onGround();
    }

    @Unique private void hexie$forgetSupport() {
        hexie$sentCarriageId = -1;
        hexie$sentCarriageUuid = null;
        hexie$sentLocal = null;
    }
}
