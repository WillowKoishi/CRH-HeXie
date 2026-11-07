package willow.train.hexie.mixins.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.blocks.emu.crh2a.CRH2DoorInteraction;
import willow.train.hexie.blocks.coupler.CouplerInteraction;

/** Keep vanilla handling, permitting only validated model surfaces outside their source cells. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinDoorInteractionPacket {
    @Shadow public ServerPlayer player;

    @WrapOperation(method = "handleUseItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 hexie$validateExtendedDoorHit(Vec3 location, Vec3 centre, Operation<Vec3> original,
                                               ServerboundUseItemOnPacket packet) {
        // Both coordinates belong to the target block's storage space, including a Sable plot.
        Vec3 delta = original.call(location, centre);
        if (Math.abs(delta.x) < 1.0000001 && Math.abs(delta.y) < 1.0000001 && Math.abs(delta.z) < 1.0000001)
            return delta;
        // This alters only the following coordinate sanity check. The actual hit, native
        // block reach, permissions, item use and block updates remain in the native handler.
        return CRH2DoorInteraction.validExtendedHit(player, packet.getHitResult())
                || CouplerInteraction.validExtendedHit(player, packet.getHitResult()) ? Vec3.ZERO : delta;
    }
}
