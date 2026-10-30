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
import willow.train.hexie.compat.create.CarriagePlayerSupport;
import willow.train.hexie.network.c2s.CarriagePlayerMovePacket;
import willow.train.hexie.client.events.CarriageStandingClient;

@Mixin(LocalPlayer.class)
public abstract class MixinCarriageLocalPlayer {
    @Unique private boolean hexie$sentSupport;
    @WrapOperation(method = "sendPosition", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void hexie$relativeMovement(ClientPacketListener connection, Packet<?> packet, Operation<Void> original) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        var car = CarriagePlayerSupport.carriage(player);
        if (!(packet instanceof ServerboundMovePlayerPacket movement) || player.isPassenger()
                || car == null && !hexie$sentSupport) { original.call(connection, packet); return; }
        CarriageStandingClient.afterCollisionMovement(() -> hexie$sendResolvedMovement(player, movement));
    }

    @Unique private void hexie$sendResolvedMovement(LocalPlayer player, ServerboundMovePlayerPacket movement) {
        if (net.minecraft.client.Minecraft.getInstance().player != player || player.isPassenger()) return;
        var car = CarriagePlayerSupport.carriage(player);
        Vec3 position = car == null ? player.position() : CarriagePlayerSupport.localPosition(player);
        PacketDistributor.sendToServer(new CarriagePlayerMovePacket(car == null ? -1 : car.getId(),
                car == null ? new UUID(0, 0) : car.getUUID(), position,
                movement.getYRot(player.getYRot()), movement.getXRot(player.getXRot()),
                car != null ? CarriagePlayerSupport.hasFloor(car, player.position(), player.getBbWidth()) : player.onGround()));
        hexie$sentSupport = car != null;
    }
}
