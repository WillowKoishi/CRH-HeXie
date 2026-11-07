package willow.train.hexie.item;

import willow.train.hexie.blocks.coupler.CouplerGeometry;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint.Point;

/** Removable, reversible CRH2-auto / standard-knuckle converter, using the existing adapter stack. */
public final class RescueCouplerItem extends CouplerAdapterItem {
    public static final String ID = "hexie:crh2a_rescue_coupler";
    public static final String AUTO_MODEL = "hexie:crh2_auto", KNUCKLE_MODEL = "railways:knuckle";
    private static final class GeometryHolder {
        private static final CouplerGeometry VALUE = CouplerGeometry.load("/data/hexie/crh2a_rescue_coupler_geometry.json");
    }
    public static CouplerGeometry geometry() { return GeometryHolder.VALUE; }

    public RescueCouplerItem(Properties properties) {
        super(properties, AUTO_MODEL, KNUCKLE_MODEL, -geometry().contact().z,
                new Point(geometry().contact().x, geometry().contact().y, geometry().contact().z));
    }
}
