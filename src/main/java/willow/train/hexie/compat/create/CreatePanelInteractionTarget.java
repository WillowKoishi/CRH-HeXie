package willow.train.hexie.compat.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.api.interaction.BlockControlContext;
import willow.train.hexie.api.interaction.BlockControlHost;
import willow.train.hexie.blocks.parts.PartStructures;
import willow.train.hexie.blocks.signs.PanelAttachmentHit;
import willow.train.hexie.compat.sable.SableInteractionCoordinates;

/** Shared captured-surface validation for copying and opening a content editor. */
public record CreatePanelInteractionTarget(CapturedView view, BlockPos rootPos, BlockPos clickedPos,
        BlockHitResult hit) {
    public PanelAttachmentHit.Target attachment() { return PanelAttachmentHit.resolve(view, clickedPos, hit); }

    public static CreatePanelInteractionTarget validate(ServerPlayer player, AbstractContraptionEntity carrier,
            BlockHitResult hit) {
        if (player.isSpectator() || carrier.isRemoved() || carrier.level() != player.level()
                || carrier.getContraption() == null || !Double.isFinite(hit.getLocation().x)
                || !Double.isFinite(hit.getLocation().y) || !Double.isFinite(hit.getLocation().z)
                || !carrier.canInteractWithBlock(player, hit.getBlockPos(), 1)) return null;
        var localHit = hit.getLocation();
        var storedHit = carrier instanceof SnapshotCarriageEntity snapshot
                ? CarriagePlayerSupport.frame(snapshot, 1).world(localHit) : carrier.toGlobalVector(localHit, 1);
        var worldHit = SableInteractionCoordinates.logical(carrier.level(), carrier.blockPosition()).toWorld(storedHit);
        var eye = SableInteractionCoordinates.logicalWorld(player.level(), player.getEyePosition());
        double reach = player.blockInteractionRange() + 1;
        if (eye.distanceToSqr(worldHit) > reach * reach) return null;
        var view = new CapturedView(carrier);
        var info = carrier.getContraption().getBlocks().get(hit.getBlockPos());
        if (info == null) return null;
        var shape = info.state().getShape(view, hit.getBlockPos());
        var direction = Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(.125);
        var host = BlockControlHost.find(info.state());
        var picked = host == null || !host.nativeInteraction() ? null : host.pickControl(
                new BlockControlContext(view, hit.getBlockPos(), carrier, 1),
                localHit.add(direction), localHit.subtract(direction));
        var surface = picked == null ? shape.clip(localHit.add(direction), localHit.subtract(direction), hit.getBlockPos())
                : picked.blockHit();
        if (surface == null || surface.getDirection() != hit.getDirection()
                || surface.getLocation().distanceToSqr(localHit) > 1.0 / 4096) return null;
        var root = PartStructures.resolve(view, hit.getBlockPos());
        var cells = root == null ? java.util.Set.of(hit.getBlockPos())
                : root.structure().partCells(view, root.pos(), root.state()).keySet();
        for (var cell : cells) {
            var stored = carrier instanceof SnapshotCarriageEntity snapshot
                    ? CarriagePlayerSupport.frame(snapshot, 1).world(Vec3.atCenterOf(cell))
                    : carrier.toGlobalVector(Vec3.atCenterOf(cell), 1);
            if (!player.level().mayInteract(player, BlockPos.containing(stored))) return null;
        }
        return new CreatePanelInteractionTarget(view, root == null ? hit.getBlockPos() : root.pos(), hit.getBlockPos(), hit);
    }

    /** Isolated read-only views never bind captured entities to the player's real world. */
    public static class CapturedView implements BlockGetter {
        private final AbstractContraptionEntity carrier;
        private final Map<BlockPos, BlockEntity> entities = new HashMap<>();
        public CapturedView(AbstractContraptionEntity carrier) { this.carrier = carrier; }
        @Override public BlockState getBlockState(BlockPos pos) {
            var info = carrier.getContraption().getBlocks().get(pos);
            return info == null ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : info.state();
        }
        @Override public BlockEntity getBlockEntity(BlockPos pos) {
            if (entities.containsKey(pos)) return entities.get(pos);
            var info = carrier.getContraption().getBlocks().get(pos);
            BlockEntity entity = null;
            if (info != null) {
                if (info.nbt() != null) entity = BlockEntity.loadStatic(pos, info.state(), info.nbt().copy(), carrier.registryAccess());
                if (entity == null && info.state().getBlock() instanceof EntityBlock factory) {
                    entity = factory.newBlockEntity(pos, info.state());
                    if (entity != null && info.nbt() != null) entity.loadWithComponents(info.nbt().copy(), carrier.registryAccess());
                }
            }
            entities.put(pos.immutable(), entity);
            return entity;
        }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public int getHeight() { return carrier.level().getHeight(); }
        @Override public int getMinBuildHeight() { return carrier.level().getMinBuildHeight(); }
    }
}
