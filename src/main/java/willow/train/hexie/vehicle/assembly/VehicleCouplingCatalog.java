package willow.train.hexie.vehicle.assembly;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.CouplingBridge;
import willow.train.hexie.core.api.vehicle.Point3;
import willow.train.hexie.vehicle.runtime.CouplerAdapter;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint;

/** Decodes coupling blocks without depending on the source of the assembly trigger. */
public final class VehicleCouplingCatalog {
    private VehicleCouplingCatalog() {}

    public static List<CouplingEndpoint> decode(CarriageSnapshot snapshot, Direction forward) {
        if (!forward.getAxis().isHorizontal())
            throw new IllegalArgumentException("Carriage forward direction must be horizontal");
        var endpoints = new ArrayList<CouplingEndpoint>();
        for (var block : snapshot.blocks()) {
            var coupler = HexieBridges.couplerBlock().couplerBlock().of(block.state());
            if (coupler == null) continue;
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
                    northForward(block.position(), forward), CouplingBridge.CouplingEndpointFacing.valueOf(relative.name()),
                    new CouplingEndpoint.Axis(coupler.axis().x(), coupler.axis().z()), coupler.start(), coupler.complete(), coupler.kind(), coupler.maxExtension(),
                    coupler.model(), (CouplerAdapter) HexieBridges.couplerBlock().couplerAdapterData().read(block.entityData()),
                    CouplerDataRegistry.capture(coupler, block.state())));
        }
        return List.copyOf(endpoints);
    }

    public static UUID endpointId(UUID carriage, BlockPos position) {
        return UUID.nameUUIDFromBytes((carriage + ":coupler:" + position.asLong()).getBytes(StandardCharsets.UTF_8));
    }

    private static Point3 northForward(BlockPos position, Direction forward) {
        return switch (forward) {
            case NORTH -> new Point3(position.getX(), position.getY(), position.getZ());
            case EAST -> new Point3(position.getZ(), position.getY(), -position.getX());
            case SOUTH -> new Point3(-position.getX(), position.getY(), -position.getZ());
            case WEST -> new Point3(-position.getZ(), position.getY(), position.getX());
            default -> throw new IllegalArgumentException("Carriage forward direction must be horizontal");
        };
    }
}
