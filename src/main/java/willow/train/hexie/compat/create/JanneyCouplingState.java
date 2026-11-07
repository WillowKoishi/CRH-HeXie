package willow.train.hexie.compat.create;

import java.util.HashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import willow.train.hexie.blocks.coupler.JanneyCouplerBlock;
import willow.train.hexie.blocks.coupler.RescueCouplerState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import willow.train.hexie.vehicle.assembly.CouplerDataRegistry;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint;
import willow.train.hexie.vehicle.runtime.VehicleRuntime;

/** Mechanical coupling state drives the existing persisted, smoothly animated knuckle state. */
final class JanneyCouplingState {
    private JanneyCouplingState() {}

    static void sync(SnapshotCarriageEntity carriage, VehicleRuntime runtime, Direction forward) {
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
            if (info.state().getBlock() instanceof JanneyCouplerBlock block) {
                boolean open = !coupled && endpoint.adapter() == null;
                if (info.state().getValue(BlockStateProperties.OPEN) != open) {
                    next = SnapshotCouplerModelInteraction.withOpen(info, open, carriage.level().getGameTime());
                    var visual = endpoint.visual();
                    runtime.updateCouplerVisual(id, visual == null ? CouplerDataRegistry.capture(block, next.state())
                            : new CouplingEndpoint.Visual(visual.facing(), withOpen(visual.fixed(), open),
                                    withOpen(visual.shiftedFixed(), open), visual.moving(), visual.origin(), visual.movingOffset()));
                }
            }
            var data = RescueCouplerState.sync(next.nbt(), endpoint.adapter(), endpoint.model(), coupled,
                    carriage.level().getGameTime());
            if (RescueCouplerState.installed(endpoint.adapter()))
                data = RescueCouplerState.withOpen(data,
                        RescueCouplerState.exposed(endpoint.adapter(), endpoint.model()) && !coupled,
                        carriage.level().getGameTime());
            if (next.nbt() == null ? !data.isEmpty() : !data.equals(next.nbt()))
                next = new StructureBlockInfo(next.pos(), next.state(), data);
            if (next != info) carriage.setBlock(info.pos(), next);
        }
    }

    private static CouplingEndpoint.RenderState withOpen(CouplingEndpoint.RenderState state, boolean open) {
        if (!state.block().equals("hexie:general_janney_coupler")) return state;
        var properties = new HashMap<>(state.properties());
        properties.put(BlockStateProperties.OPEN.getName(), Boolean.toString(open));
        return new CouplingEndpoint.RenderState(state.block(), properties);
    }
}
