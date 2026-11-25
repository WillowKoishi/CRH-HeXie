package willow.train.hexie.compat.create.interaction;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import java.util.HashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.interaction.ToolOpsBridge.FloorSwitcherItemView;
import willow.train.hexie.bridge.vehicle.CouplingBridge.CouplingEndpointRenderStateView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2EndFaceBlockView;
import willow.train.hexie.compat.create.motion.SnapshotCarriageEntity;

/** One end-face owner cell, one appearance change; coupling mechanics and BE data stay intact. */
public final class CreateEndFaceVariantInteraction extends MovingInteractionBehaviour {
    @Override public boolean handlePlayerInteraction(Player player, InteractionHand hand, BlockPos position,
            AbstractContraptionEntity entity) {
        if (!(player.getItemInHand(hand).getItem() instanceof FloorSwitcherItemView)
                || player.isSpectator() || !player.mayBuild() || entity.getContraption() == null
                || !entity.canInteractWithBlock(player, position, 4)) return false;
        var info = entity.getContraption().getBlocks().get(position);
        if (info == null || !(info.state().getBlock() instanceof CRH2EndFaceBlockView block)) return false;
        if (player.isShiftKeyDown()) {
            HexieBridges.toolOps().floorSwitcherItem().switchMode(player, hand);
            return true;
        }
        if (entity.level().isClientSide) return true;
        var next = info.state().cycle(HexieBridges.carriageBlock().crh2EndFaceBlock().variant());
        if (entity instanceof SnapshotCarriageEntity snapshot) {
            var motion = snapshot.motion();
            if (motion == null) return true;
            var runtime = motion.runtime();
            var endpoint = HexieBridges.coupling().vehicleCouplingCatalog().endpointId(snapshot.getUUID(), position);
            var endpointDefinition = runtime.definition(snapshot.getUUID()).couplings().stream()
                    .filter(e -> e.id().equals(endpoint)).findFirst().orElse(null);
            if (endpointDefinition == null) return true;
            var previous = endpointDefinition.visual();
            // Legacy captured origins may already include floor compensation. Only change model selection.
            var visual = previous == null ? HexieBridges.coupling().couplerDataRegistry().capture(block, next)
                    : HexieBridges.coupling().createCouplingEndpointVisual(previous.facing(), variant(previous.fixed(), next),
                            variant(previous.shiftedFixed(), next), previous.moving(), previous.origin(), previous.movingOffset());
            runtime.updateCouplerVisual(endpoint, visual);
            entity.setBlock(position, new StructureBlockInfo(position, next, info.nbt()));
            motion.applyCurrent(snapshot);
            snapshot.syncCarriage();
            // The other car's cable eligibility also depends on this endpoint's captured appearance.
            var partner = runtime.couplingPartner(endpoint);
            if (partner != null && entity.level() instanceof ServerLevel level) {
                for (var definition : runtime.definitions()) {
                    if (definition.id().equals(snapshot.getUUID())
                            || definition.couplings().stream().noneMatch(e -> e.id().equals(partner))) continue;
                    if (level.getEntity(definition.id()) instanceof SnapshotCarriageEntity other
                            && other.motion() == motion) {
                        motion.applyCurrent(other);
                        other.syncCarriage();
                    }
                }
            }
        } else {
            entity.setBlock(position, new StructureBlockInfo(position, next, info.nbt()));
        }
        HexieBridges.carriageBlock().crh2EndFaceBlock().showVariant(player, next);
        return true;
    }

    private static CouplingEndpointRenderStateView variant(CouplingEndpointRenderStateView saved,
            net.minecraft.world.level.block.state.BlockState next) {
        var properties = new HashMap<>(saved.properties());
        properties.put(HexieBridges.carriageBlock().crh2EndFaceBlock().variant().getName(), HexieBridges.carriageBlock().crh2EndFaceBlock().type(next).getSerializedName());
        return HexieBridges.coupling().createCouplingEndpointRenderState(saved.block(), properties);
    }
}
