package willow.train.hexie.vehicle.assembly;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import willow.train.hexie.blocks.coupler.CouplerBlock;
import willow.train.hexie.blocks.coupler.CouplerAdapterData;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint.Point;

/** Decodes coupling blocks without depending on the source of the assembly trigger. */
public final class VehicleCouplingCatalog {
    private VehicleCouplingCatalog() {}

    public static List<CouplingEndpoint> decode(CarriageSnapshot snapshot, Direction forward) {
        if (!forward.getAxis().isHorizontal())
            throw new IllegalArgumentException("Carriage forward direction must be horizontal");
        var endpoints = new ArrayList<CouplingEndpoint>();
        for (var block : snapshot.blocks()) {
            if (!(block.state().getBlock() instanceof CouplerBlock coupler)) continue;
            Direction facing = coupler.facing(block.state());
            if (!facing.getAxis().isHorizontal())
                throw new IllegalArgumentException("Coupling direction must be horizontal");
            Direction relative = switch (forward) {
                case NORTH -> facing;
                case EAST -> facing.getCounterClockWise();
                case SOUTH -> facing.getOpposite();
                case WEST -> facing.getClockWise();
                default -> throw new IllegalArgumentException("Carriage forward direction must be horizontal");
            };
            endpoints.add(new CouplingEndpoint(endpointId(snapshot.id(), block.position()),
                    northForward(block.position(), forward), CouplingEndpoint.Facing.valueOf(relative.name()),
                    coupler.pivot(), coupler.start(), coupler.complete(), coupler.kind(), coupler.maxExtension(),
                    coupler.model(), CouplerAdapterData.read(block.entityData())));
        }
        return List.copyOf(endpoints);
    }

    public static UUID endpointId(UUID carriage, BlockPos position) {
        return UUID.nameUUIDFromBytes((carriage + ":coupler:" + position.asLong()).getBytes(StandardCharsets.UTF_8));
    }

    private static Point northForward(BlockPos position, Direction forward) {
        return switch (forward) {
            case NORTH -> new Point(position.getX(), position.getY(), position.getZ());
            case EAST -> new Point(position.getZ(), position.getY(), -position.getX());
            case SOUTH -> new Point(-position.getX(), position.getY(), -position.getZ());
            case WEST -> new Point(-position.getZ(), position.getY(), position.getX());
            default -> throw new IllegalArgumentException("Carriage forward direction must be horizontal");
        };
    }
}
