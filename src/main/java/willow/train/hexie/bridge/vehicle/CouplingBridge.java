package willow.train.hexie.bridge.vehicle;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.world.CouplerBlockBridge;
import willow.train.hexie.core.api.vehicle.Point3;

/** Coupling definitions, adapter chains and endpoint geometry shared by assembly, blocks and rendering. */
public interface CouplingBridge {
    CouplerAdapterOperations couplerAdapter();

    interface CouplerAdapterOperations {
        CouplerAdapterView create(String itemId, String firstModel, String secondModel, double thickness,
                List<CouplerAdapterView> layers);
        CouplerAdapterView create(String itemId, String firstModel, String secondModel, double thickness,
                Point3 offset);
        CouplerAdapterView create(String itemId, String firstModel, String secondModel, double thickness,
                List<CouplerAdapterView> layers, Point3 offset);
        CouplerAdapterView append(String baseModel, CouplerAdapterView installed, CouplerAdapterView next);
    }

    CouplerAnglesOperations couplerAngles();

    interface CouplerAnglesOperations {
        CouplerAnglesView create(double yaw, double pitch);
        CouplerAnglesView zero();
        CouplerAnglesView from(CouplingEndpointView endpoint,
                VehicleBridge.VehicleConstraintSolverBodyPoseView body,
                VehicleBridge.VehicleConstraintSolverEndpointPoseView pose);
    }

    CouplerDataRegistryOperations couplerDataRegistry();

    interface CouplerDataRegistryOperations {
        CouplerDataRegistryDefinitionView find(BlockState state);
        CouplingEndpointVisualView capture(CouplerBlockBridge.CouplerBlockView hook, BlockState state);
        BlockState renderState(CouplingEndpointRenderStateView saved);
        void install(String json);
    }

    CouplingEndpointAxisView createCouplingEndpointAxis(double x, double z);

    interface CouplingEndpointOperations {
        double DEFAULT_MAX_EXTENSION = 0.5;
    }

    CouplingEndpointRenderStateView createCouplingEndpointRenderState(String block, Map<String, String> properties);

    CouplingEndpointVisualView createCouplingEndpointVisual(CouplingEndpointFacing facing,
            CouplingEndpointRenderStateView fixed, CouplingEndpointRenderStateView shiftedFixed,
            CouplingEndpointRenderStateView moving, Point3 origin, Point3 movingOffset);

    CouplingRulesOperations couplingRules();

    interface CouplingRulesOperations {
        void directOnly(String model);
    }

    VehicleCouplingCatalogOperations vehicleCouplingCatalog();

    interface VehicleCouplingCatalogOperations {
        List<? extends CouplingEndpointView> decode(CarriageSnapshotView snapshot, Direction forward);
        UUID endpointId(UUID carriage, BlockPos position);
    }

    interface CouplerDataRegistryDefinitionView extends CouplerBlockBridge.CouplerBlockView {
        ResourceLocation id();
        String model();
        Direction direction();
        CouplingEndpointAxisView axis();
        Point3 start();
        Point3 complete();
        double maxExtension();
        BlockState fixedState();
        BlockState movingState();
        Point3 movingOffset();
    }

    interface CouplerAdapterView {
        List<? extends CouplerAdapterView> items();
        CouplerAdapterView withoutOuter(String baseModel);
        CouplerAdapterView outer();
        Point3 offsetFrom(String mountedModel);
        String otherModel(String model);
        String itemId();
        String firstModel();
        String secondModel();
        double thickness();
        List<? extends CouplerAdapterView> layers();
        Point3 offset();
    }

    interface CouplerAnglesView {
        CouplerAnglesView interpolate(CouplerAnglesView next, double fraction);
        double yaw();
        double pitch();
    }

    interface CouplingEndpointAxisView {
        Point3 atHeight(double height);
        double x();
        double z();
    }

    enum CouplingEndpointFacing {
        NORTH, EAST, SOUTH, WEST
    }

    enum CouplingEndpointKind {
        RIGID, ARTICULATED
    }

    interface CouplingEndpointRenderStateView {
        String block();
        Map<String, String> properties();
    }

    interface CouplingEndpointVisualView {
        CouplingEndpointFacing facing();
        CouplingEndpointRenderStateView fixed();
        CouplingEndpointRenderStateView shiftedFixed();
        CouplingEndpointRenderStateView moving();
        Point3 origin();
        Point3 movingOffset();
    }
}
