package willow.train.hexie.bridge.vehicle;

import java.util.UUID;
import willow.train.hexie.core.api.vehicle.Point3;

public interface CouplingEndpointView {
    String effectiveModel();
    boolean matches(CouplingEndpointView other);
    Point3 rotated(Point3 point);
    Point3 axisPoint();
    UUID id();
    Point3 blockCenter();
    CouplingBridge.CouplingEndpointFacing facing();
    CouplingBridge.CouplingEndpointAxisView axis();
    Point3 complete();
    CouplingBridge.CouplingEndpointKind kind();
    String model();
    CouplingBridge.CouplerAdapterView adapter();
    CouplingBridge.CouplingEndpointVisualView visual();
    Point3 start();
    double maxExtension();
}
