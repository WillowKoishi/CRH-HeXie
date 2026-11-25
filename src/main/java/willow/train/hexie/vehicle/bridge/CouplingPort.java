package willow.train.hexie.vehicle.bridge;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.vehicle.CarriageSnapshotView;
import willow.train.hexie.bridge.vehicle.CouplingBridge;
import willow.train.hexie.bridge.vehicle.CouplingEndpointView;
import willow.train.hexie.bridge.vehicle.VehicleBridge;
import willow.train.hexie.bridge.world.CouplerBlockBridge;
import willow.train.hexie.core.api.vehicle.Point3;
import willow.train.hexie.vehicle.assembly.CarriageSnapshot;
import willow.train.hexie.vehicle.assembly.CouplerDataRegistry;
import willow.train.hexie.vehicle.assembly.VehicleCouplingCatalog;
import willow.train.hexie.vehicle.kinematics.VehicleConstraintSolver;
import willow.train.hexie.vehicle.runtime.CouplerAdapter;
import willow.train.hexie.vehicle.runtime.CouplerAngles;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint;
import willow.train.hexie.vehicle.runtime.CouplingRules;

final class CouplingPort implements CouplingBridge {
    private final CouplerAdapterOperations couplerAdapter = new CouplerAdapterOperations() {
        @Override
        public CouplerAdapterView create(String itemId, String firstModel, String secondModel, double thickness,
                List<CouplerAdapterView> layers) {
            return new CouplerAdapter(itemId, firstModel, secondModel, thickness, layers.stream().map(CouplerAdapter.class::cast).toList());
        }

        @Override
        public CouplerAdapterView create(String itemId, String firstModel, String secondModel, double thickness,
                Point3 offset) {
            return new CouplerAdapter(itemId, firstModel, secondModel, thickness, offset);
        }

        @Override
        public CouplerAdapterView create(String itemId, String firstModel, String secondModel, double thickness,
                List<CouplerAdapterView> layers, Point3 offset) {
            return new CouplerAdapter(itemId, firstModel, secondModel, thickness, layers.stream().map(CouplerAdapter.class::cast).toList(), offset);
        }

        @Override
        public CouplerAdapterView append(String baseModel, CouplerAdapterView installed, CouplerAdapterView next) {
            return CouplerAdapter.append(baseModel, (CouplerAdapter) installed, (CouplerAdapter) next);
        }
    };

    private final CouplerAnglesOperations couplerAngles = new CouplerAnglesOperations() {
        @Override
        public CouplerAnglesView create(double yaw, double pitch) {
            return new CouplerAngles(yaw, pitch);
        }

        @Override
        public CouplerAnglesView zero() {
            return CouplerAngles.ZERO;
        }

        @Override
        public CouplerAnglesView from(CouplingEndpointView endpoint,
                VehicleBridge.VehicleConstraintSolverBodyPoseView body,
                VehicleBridge.VehicleConstraintSolverEndpointPoseView pose) {
            return CouplerAngles.from((CouplingEndpoint) endpoint, (VehicleConstraintSolver.BodyPose) body, (VehicleConstraintSolver.EndpointPose) pose);
        }
    };

    private final CouplerDataRegistryOperations couplerDataRegistry = new CouplerDataRegistryOperations() {
        @Override
        public CouplerDataRegistryDefinitionView find(BlockState state) {
            return CouplerDataRegistry.find(state);
        }

        @Override
        public CouplingEndpointVisualView capture(CouplerBlockBridge.CouplerBlockView hook, BlockState state) {
            return CouplerDataRegistry.capture(hook, state);
        }

        @Override
        public BlockState renderState(CouplingEndpointRenderStateView saved) {
            return CouplerDataRegistry.renderState((CouplingEndpoint.RenderState) saved);
        }

        @Override
        public void install(String json) {
            CouplerDataRegistry.install(json);
        }
    };

    private final VehicleCouplingCatalogOperations vehicleCouplingCatalog = new VehicleCouplingCatalogOperations() {
        @Override
        public List<? extends CouplingEndpointView> decode(CarriageSnapshotView snapshot, Direction forward) {
            return VehicleCouplingCatalog.decode((CarriageSnapshot) snapshot, forward);
        }

        @Override
        public UUID endpointId(UUID carriage, BlockPos position) {
            return VehicleCouplingCatalog.endpointId(carriage, position);
        }
    };

    @Override
    public CouplerAdapterOperations couplerAdapter() {
        return couplerAdapter;
    }

    @Override
    public CouplerAnglesOperations couplerAngles() {
        return couplerAngles;
    }

    @Override
    public CouplerDataRegistryOperations couplerDataRegistry() {
        return couplerDataRegistry;
    }

    @Override
    public CouplingEndpointAxisView createCouplingEndpointAxis(double x, double z) {
        return new CouplingEndpoint.Axis(x, z);
    }

    @Override
    public CouplingEndpointRenderStateView createCouplingEndpointRenderState(String block,
            Map<String, String> properties) {
        return new CouplingEndpoint.RenderState(block, properties);
    }

    @Override
    public CouplingEndpointVisualView createCouplingEndpointVisual(CouplingEndpointFacing facing,
            CouplingEndpointRenderStateView fixed, CouplingEndpointRenderStateView shiftedFixed,
            CouplingEndpointRenderStateView moving, Point3 origin, Point3 movingOffset) {
        return new CouplingEndpoint.Visual(facing, (CouplingEndpoint.RenderState) fixed, (CouplingEndpoint.RenderState) shiftedFixed, (CouplingEndpoint.RenderState) moving, origin, movingOffset);
    }

    @Override
    public CouplingRulesOperations couplingRules() {
        return CouplingRules::directOnly;
    }

    @Override
    public VehicleCouplingCatalogOperations vehicleCouplingCatalog() {
        return vehicleCouplingCatalog;
    }
}
