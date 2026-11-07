package willow.train.hexie.mixins.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ContraptionCollider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.createmod.catnip.platform.services.NetworkHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.blocks.emu.crh2a.CRH2DoorBlock;
import willow.train.hexie.compat.create.CarriagePlayerSupport;
import willow.train.hexie.compat.create.ContraptionInteractionTargets;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;

/** Keep Create's shape collision, replace only platform movement for our supported players. */
@Mixin(ContraptionCollider.class)
public abstract class MixinCarriagePlayerCollision {
    @WrapOperation(method = "collideEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"), remap = false)
    private static List<Entity> hexie$includeSupportedPlayers(Level world, Class<Entity> type, AABB bounds,
            Predicate<? super Entity> predicate, Operation<List<Entity>> original,
            @Local(argsOnly = true) AbstractContraptionEntity contraption) {
        List<Entity> entities = original.call(world, type, bounds, predicate);
        if (!world.isClientSide || !(contraption instanceof SnapshotCarriageEntity car)) return entities;
        List<Entity> supplemented = null;
        // Section visibility can exclude an always-ticking player from the spatial query.
        // Keep that player's existing floor collision even before terrain arrives.
        for (Player player : world.players()) {
            if (CarriagePlayerSupport.attachedCarriage(player) != car || entities.contains(player)
                    || !bounds.intersects(player.getBoundingBox()) || !predicate.test(player)) continue;
            if (supplemented == null) supplemented = new ArrayList<>(entities);
            supplemented.add(player);
        }
        return supplemented == null ? entities : supplemented;
    }

    // Create passes the real level with local positions when a player needs detailed collision.
    @WrapOperation(method = "getPotentiallyCollidedShapes", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"), remap = false)
    private static VoxelShape hexie$doorCollisionWorld(BlockState state, BlockGetter world, BlockPos localPos,
            Operation<VoxelShape> original, @Local(argsOnly = true) Contraption contraption) {
        if (state.getBlock() instanceof CRH2DoorBlock) world = contraption.getContraptionWorld();
        return original.call(state, world, localPos);
    }

    @WrapOperation(method = "collideEntities", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/contraptions/Contraption;getSimplifiedEntityColliders()Lcom/simibubi/create/foundation/collision/CollisionList;"), remap = false)
    private static com.simibubi.create.foundation.collision.CollisionList hexie$headDetailedCollision(
            com.simibubi.create.content.contraptions.Contraption contraption,
            Operation<com.simibubi.create.foundation.collision.CollisionList> original,
            @Local(name = "entity") Entity entity) {
        if (entity instanceof Player && ContraptionInteractionTargets.hasPlayerShells(contraption)) return null;
        return original.call(contraption);
    }

    @WrapOperation(method = "collideEntities", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/contraptions/ContraptionCollider;getPotentiallyCollidedShapes(Lnet/minecraft/world/level/Level;Lcom/simibubi/create/content/contraptions/Contraption;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/world/phys/shapes/Shapes$DoubleLineConsumer;)V"), remap = false)
    private static void hexie$headShell(net.minecraft.world.level.Level world,
            com.simibubi.create.content.contraptions.Contraption contraption, net.minecraft.world.phys.AABB query,
            net.minecraft.world.phys.shapes.Shapes.DoubleLineConsumer out, Operation<Void> original,
            @Local(name = "entity") Entity entity) {
        original.call(world, contraption, query, out);
        if (!(entity instanceof Player)) return;
        ContraptionInteractionTargets.collectPlayerShells(contraption, query, out);
    }

    @Inject(method = "collideEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;setPos(DDD)V", ordinal = 0,
            shift = At.Shift.AFTER), remap = false)
    private static void hexie$touchBody(AbstractContraptionEntity contraption, CallbackInfo ci,
            @Local(name = "entity") Entity entity) {
        if (contraption instanceof SnapshotCarriageEntity car) CarriagePlayerSupport.contact(car, entity);
    }

    @Inject(method = "collideEntities", at = @At("HEAD"), remap = false)
    private static void hexie$carry(AbstractContraptionEntity entity, CallbackInfo ci) {
        if (entity instanceof SnapshotCarriageEntity car) CarriagePlayerSupport.advance(car);
    }

    @Inject(method = "collideEntities", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/contraptions/ContraptionCollider;getPlayerType(Lnet/minecraft/world/entity/Entity;)Lcom/simibubi/create/content/contraptions/ContraptionCollider$PlayerType;"), remap = false)
    private static void hexie$relativeCollision(AbstractContraptionEntity contraption, CallbackInfo ci,
            @Local(name = "entity") Entity entity, @Local(name = "contraptionMotion") LocalRef<Vec3> motion) {
        if (!contraption.level().isClientSide || !(contraption instanceof SnapshotCarriageEntity car)) return;
        // This local is shared by the loop: restore it for every non-supported entity too.
        motion.set(CarriagePlayerSupport.supportedBy(entity, car) ? Vec3.ZERO
                : car.position().subtract(car.getPrevPositionVec()));
    }

    @WrapOperation(method = "collideEntities", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;getContactPointMotion(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"), remap = false)
    private static Vec3 hexie$noSecondCarry(AbstractContraptionEntity contraption, Vec3 point,
            Operation<Vec3> original, @Local(name = "entity") Entity entity) {
        if (contraption instanceof SnapshotCarriageEntity && entity instanceof Player) return Vec3.ZERO;
        return original.call(contraption, point);
    }

    @WrapOperation(method = "collideEntities", at = @At(value = "INVOKE",
            target = "Lnet/createmod/catnip/platform/services/NetworkHelper;sendToServer(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V",
            ordinal = 1), remap = false)
    private static void hexie$singleStandingReport(NetworkHelper network, CustomPacketPayload packet,
            Operation<Void> original, @Local(argsOnly = true) AbstractContraptionEntity contraption,
            @Local(name = "entity") Entity entity) {
        // Our post-collision local-feet packet also owns ground/fall/walking state.
        // Do not run Create's second server movement update for the same player.
        if (contraption instanceof SnapshotCarriageEntity car && entity instanceof Player player
                && CarriagePlayerSupport.attachedCarriage(player) == car) return;
        original.call(network, packet);
    }
}
