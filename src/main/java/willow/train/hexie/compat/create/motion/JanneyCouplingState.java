package willow.train.hexie.compat.create.motion;

import java.util.HashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.CouplingBridge.CouplingEndpointRenderStateView;
import willow.train.hexie.bridge.vehicle.VehicleRuntimeView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.SS7DHeadBlockView;
import willow.train.hexie.bridge.world.CouplerBlockBridge.JanneyCouplerBlockView;
import willow.train.hexie.compat.create.interaction.SnapshotCouplerModelInteraction;

/** Mechanical coupling state drives the existing persisted, smoothly animated knuckle state. */
final class JanneyCouplingState {
    private JanneyCouplingState() {}

    static void sync(SnapshotCarriageEntity carriage, VehicleRuntimeView runtime, Direction forward) {
        if (carriage.level().isClientSide || carriage.getContraption() == null) return;
        var endpoints = runtime.definition(carriage.getUUID()).couplings();
        // The definition already indexes the few hooks; avoid scanning every carriage block each tick.
        for (var endpoint : endpoints) {
            var center = endpoint.blockCenter();
            int x = (int) Math.round(center.x()), y = (int) Math.round(center.y()), z = (int) Math.round(center.z());
            var position = switch (forward) {
                case NORTH -> new BlockPos(x, y, z);
                case EAST -> new BlockPos(-z, y, x);
                case SOUTH -> new BlockPos(-x, y, -z);
                case WEST -> new BlockPos(z, y, -x);
                default -> throw new IllegalArgumentException("Carriage forward must be horizontal");
            };
            var info = carriage.getContraption().getBlocks().get(position);
            if (info == null) continue;
            var id = endpoint.id();
            boolean coupled = runtime.couplingPartner(id) != null;
            var next = info;
            boolean integrated = info.state().getBlock() instanceof SS7DHeadBlockView;
            if (info.state().getBlock() instanceof JanneyCouplerBlockView || integrated) {
                boolean open = !coupled && endpoint.adapter() == null;
                if (info.state().getValue(BlockStateProperties.OPEN) != open) {
                    next = integrated
                            ? new StructureBlockInfo(info.pos(), info.state().setValue(BlockStateProperties.OPEN, open), info.nbt())
                            : SnapshotCouplerModelInteraction.withOpen(info, open, carriage.level().getGameTime());
                    var visual = endpoint.visual();
                    runtime.updateCouplerVisual(id, visual == null ? HexieBridges.coupling().couplerDataRegistry().capture(HexieBridges.couplerBlock().couplerBlock().of(next.state()), next.state())
                            : HexieBridges.coupling().createCouplingEndpointVisual(visual.facing(), withOpen(visual.fixed(), open),
                                    withOpen(visual.shiftedFixed(), open), visual.moving(), visual.origin(), visual.movingOffset()));
                }
            }
            var data = HexieBridges.couplerBlock().rescueCouplerState().sync(next.nbt(), endpoint.adapter(), endpoint.model(), coupled,
                    carriage.level().getGameTime());
            if (HexieBridges.couplerBlock().rescueCouplerState().installed(endpoint.adapter()))
                data = HexieBridges.couplerBlock().rescueCouplerState().withOpen(data,
                        HexieBridges.couplerBlock().rescueCouplerState().exposed(endpoint.adapter(), endpoint.model()) && !coupled,
                        carriage.level().getGameTime());
            if (next.nbt() == null ? !data.isEmpty() : !data.equals(next.nbt()))
                next = new StructureBlockInfo(next.pos(), next.state(), data);
            if (next != info) carriage.setBlock(info.pos(), next);
        }
    }

    private static CouplingEndpointRenderStateView withOpen(CouplingEndpointRenderStateView state, boolean open) {
        if (!state.block().equals("hexie:general_janney_coupler") && !state.block().equals("hexie:ss7d_head")) return state;
        var properties = new HashMap<>(state.properties());
        properties.put(BlockStateProperties.OPEN.getName(), Boolean.toString(open));
        return HexieBridges.coupling().createCouplingEndpointRenderState(state.block(), properties);
    }
}
