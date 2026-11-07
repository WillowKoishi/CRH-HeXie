package willow.train.hexie.compat.create;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import java.util.HashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import willow.train.hexie.blocks.emu.crh2a.CRH2EndFaceBlock;
import willow.train.hexie.item.FloorSwitcherItem;
import willow.train.hexie.vehicle.assembly.CouplerDataRegistry;
import willow.train.hexie.vehicle.assembly.VehicleCouplingCatalog;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint;

/** One end-face owner cell, one appearance change; coupling mechanics and BE data stay intact. */
public final class CreateEndFaceVariantInteraction extends MovingInteractionBehaviour {
    @Override public boolean handlePlayerInteraction(Player player, InteractionHand hand, BlockPos position,
            AbstractContraptionEntity entity) {
        if (!(player.getItemInHand(hand).getItem() instanceof FloorSwitcherItem)
                || player.isSpectator() || !player.mayBuild() || entity.getContraption() == null
                || !entity.canInteractWithBlock(player, position, 4)) return false;
        var info = entity.getContraption().getBlocks().get(position);
        if (info == null || !(info.state().getBlock() instanceof CRH2EndFaceBlock block)) return false;
        if (player.isShiftKeyDown()) {
            FloorSwitcherItem.switchMode(player, hand);
            return true;
        }
        if (entity.level().isClientSide) return true;
        var next = info.state().cycle(CRH2EndFaceBlock.VARIANT);
        if (entity instanceof SnapshotCarriageEntity snapshot) {
            var motion = snapshot.motion();
            if (motion == null) return true;
            var runtime = motion.runtime();
            var endpoint = VehicleCouplingCatalog.endpointId(snapshot.getUUID(), position);
            var endpointDefinition = runtime.definition(snapshot.getUUID()).couplings().stream()
                    .filter(e -> e.id().equals(endpoint)).findFirst().orElse(null);
            if (endpointDefinition == null) return true;
            var previous = endpointDefinition.visual();
            // Legacy captured origins may already include floor compensation. Only change model selection.
            var visual = previous == null ? CouplerDataRegistry.capture(block, next)
                    : new CouplingEndpoint.Visual(previous.facing(), variant(previous.fixed(), next),
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
        CRH2EndFaceBlock.showVariant(player, next);
        return true;
    }

    private static CouplingEndpoint.RenderState variant(CouplingEndpoint.RenderState saved,
            net.minecraft.world.level.block.state.BlockState next) {
        var properties = new HashMap<>(saved.properties());
        properties.put(CRH2EndFaceBlock.VARIANT.getName(), CRH2EndFaceBlock.type(next).getSerializedName());
        return new CouplingEndpoint.RenderState(saved.block(), properties);
    }
}
