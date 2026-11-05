package willow.train.hexie.mixins.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ContraptionCollider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.blocks.emu.crh2a.CRH2DoorBlock;
import willow.train.hexie.compat.create.CarriagePlayerSupport;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;

/** Keep Create's shape collision, replace only platform movement for our supported players. */
@Mixin(ContraptionCollider.class)
public abstract class MixinCarriagePlayerCollision {
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
        if (entity instanceof Player && contraption.getBlocks().values().stream().anyMatch(info ->
                info.state().getBlock() instanceof willow.train.hexie.blocks.emu.head.CRH2HeadBlock
                        || info.state().getBlock() instanceof willow.train.hexie.blocks.emu.crh2a.CRH2EndFaceBlock)) return null;
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
        for (var info : contraption.getBlocks().values()) {
            boolean endFace = info.state().getBlock() instanceof willow.train.hexie.blocks.emu.crh2a.CRH2EndFaceBlock;
            if (!endFace && !(info.state().getBlock() instanceof willow.train.hexie.blocks.emu.head.CRH2HeadBlock)) continue;
            var facing = info.state().getValue(willow.train.hexie.blocks.emu.head.CRH2HeadBlock.FACING);
            var boxes = endFace ? willow.train.hexie.blocks.emu.crh2a.CRH2EndFaceBlock.playerBoxes(facing)
                    : willow.train.hexie.blocks.emu.head.HeadGeometry.boxes(facing);
            for (var box : boxes) {
                var b = box.move(info.pos());
                if (b.intersects(query)) out.consume(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
            }
        }
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
        if (!(contraption instanceof SnapshotCarriageEntity car)) return;
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

    @Inject(method = "collideEntities", at = @At("RETURN"), remap = false)
    private static void hexie$finish(AbstractContraptionEntity entity, CallbackInfo ci) {
        if (entity instanceof SnapshotCarriageEntity car) CarriagePlayerSupport.afterCollision(car);
    }
}
