package willow.train.hexie.mixins.mixin;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
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
    @Shadow private int aboveGroundTickCount;
    @Shadow private boolean clientIsFloating;
    @Shadow public abstract void resetPosition();
    @Shadow private boolean updateAwaitingTeleport() { throw new AssertionError(); }
    @Unique private ServerPlayer hexie$reportedPlayer;
    @Unique private int hexie$reportedCarriageId = -1;
    @Unique private UUID hexie$reportedCarriageUuid;
    @Unique private long hexie$restoreSequence;
    @Unique private long hexie$awaitingCarriageRestore;

    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void hexie$ordinaryMovement(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        // Old world reports and seated Rot packets can arrive after a server-side
        // seat exit or login placement. Only the client's placement ACK ends this handoff.
        if (hexie$waitingForClientRestore()) {
            ci.cancel();
            return;
        }
        // The first ordinary packet ends this protocol, even if an exit report was
        // skipped by a seat change. It must not leave a reusable world-position grant.
        hexie$forgetCarriage();
        CarriagePlayerSupport.detach(player);
    }

    @Unique private boolean hexie$waitingForClientRestore() {
        if (hexie$awaitingCarriageRestore == 0 || hexie$reportedPlayer != player || !player.isAlive()
                || player.isRemoved() || player.isPassenger() || player.isSleeping() || player.isChangingDimension()) return false;
        var entity = player.level().getEntity(hexie$reportedCarriageId);
        if (entity instanceof SnapshotCarriageEntity car && !car.isRemoved()
                && car.getUUID().equals(hexie$reportedCarriageUuid)) return true;
        var attached = CarriagePlayerSupport.attachedCarriage(player);
        return attached != null && attached.getId() == hexie$reportedCarriageId
                && attached.getUUID().equals(hexie$reportedCarriageUuid);
    }

    @Inject(method = "teleport(DDDFFLjava/util/Set;)V", at = @At("HEAD"))
    private void hexie$endCarriageMovement(double x, double y, double z, float yaw, float pitch,
            Set<RelativeMovement> relative, CallbackInfo ci) {
        // All server teleports retain vanilla's destination and ACK, including a
        // same-position teleport. No carriage motion rewrites a pending destination.
        hexie$forgetCarriage();
        CarriagePlayerSupport.detach(player);
    }

    @Override public void hexie$grounded() { aboveGroundTickCount = 0; clientIsFloating = false; }

    @Override public boolean hexie$canCarry() {
        // Custom movement replaces handleMovePlayer, so it must also drive vanilla's
        // pending-teleport resend timer while waiting for its normal acknowledgement.
        return !updateAwaitingTeleport() && hexie$awaitingCarriageRestore == 0;
    }

    @Override public boolean hexie$acceptCarriageMovement(Vec3 world, Vec3 localMovement, Vec3 worldMovement,
            float yaw, float pitch, boolean onGround) {
        if (!hexie$canCarry()) return false;
        boolean jumped = player.onGround() && !onGround && localMovement.y > 0;
        player.absMoveTo(world.x, world.y, world.z, Mth.wrapDegrees(yaw), Mth.wrapDegrees(pitch));
        player.setDeltaMovement(worldMovement);
        player.setKnownMovement(worldMovement);
        var support = (EntityCarriageMovementAccessor) player;
        support.hexie$setReportedOnGround(onGround);
        support.hexie$setReportedSupportingBlock(Optional.empty());
        support.hexie$setReportedOnGroundNoBlocks(onGround);
        // Only walking/jumping relative to the body contributes to fall distance
        // and walking statistics. Ground support was resolved by the client.
        // Match Create's ClientMotionPacket fall handling; an unrelated terrain
        // block beneath the current carriage pose is not its landing material.
        if (onGround) {
            if (player.fallDistance > 0)
                player.causeFallDamage(player.fallDistance, 1, player.damageSources().fall());
            player.resetFallDistance();
        } else if (localMovement.y < 0) {
            player.fallDistance -= (float) localMovement.y;
        }
        if (!player.isAlive() || player.isChangingDimension() || !hexie$canCarry()) return false;
        if (localMovement.y > 0) player.resetFallDistance();
        if (onGround) player.tryResetCurrentImpulseContext();
        if (jumped) {
            player.awardStat(Stats.JUMP);
            player.causeFoodExhaustion(player.isSprinting() ? .2F : .05F);
            net.neoforged.neoforge.common.CommonHooks.onLivingJump(player);
        }
        player.checkMovementStatistics(localMovement.x, localMovement.y, localMovement.z);
        if (!player.isAlive() || player.isChangingDimension() || !hexie$canCarry()) return false;
        player.serverLevel().getChunkSource().move(player);
        resetPosition();
        hexie$grounded();
        return true;
    }

    @Override public long hexie$beginCarriageRestore(int id, UUID uuid) {
        hexie$rememberCarriage(id, uuid);
        if (++hexie$restoreSequence == 0) ++hexie$restoreSequence;
        hexie$awaitingCarriageRestore = hexie$restoreSequence;
        hexie$grounded();
        return hexie$awaitingCarriageRestore;
    }

    @Override public void hexie$acknowledgeCarriageRestore(long token) {
        if (hexie$reportedPlayer == player && token != 0 && token == hexie$awaitingCarriageRestore)
            hexie$awaitingCarriageRestore = 0;
    }

    @Override public void hexie$rememberCarriage(int id, UUID uuid) {
        hexie$awaitingCarriageRestore = 0;
        hexie$reportedPlayer = player;
        hexie$reportedCarriageId = id;
        hexie$reportedCarriageUuid = uuid;
    }

    @Override public boolean hexie$wasCarriedBy(int id, UUID uuid) {
        return hexie$reportedPlayer == player && hexie$reportedCarriageId == id
                && uuid.equals(hexie$reportedCarriageUuid);
    }

    @Override public void hexie$forgetCarriage() {
        hexie$awaitingCarriageRestore = 0;
        hexie$reportedPlayer = null;
        hexie$reportedCarriageId = -1;
        hexie$reportedCarriageUuid = null;
    }

    @Override public void hexie$carry(Vec3 displacement) {
        // Used only by explicit seat/login placement, not periodic following.
        firstGoodX += displacement.x; firstGoodY += displacement.y; firstGoodZ += displacement.z;
        lastGoodX += displacement.x; lastGoodY += displacement.y; lastGoodZ += displacement.z;
    }
}
