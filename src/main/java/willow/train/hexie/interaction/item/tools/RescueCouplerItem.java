package willow.train.hexie.interaction.item.tools;

import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.interaction.ToolOpsBridge.RescueCouplerItemView;
import willow.train.hexie.bridge.world.WorldBlockBridge.CouplerGeometryView;
import willow.train.hexie.core.api.vehicle.Point3;

/** Removable, reversible CRH2-auto / standard-knuckle converter, using the existing adapter stack. */
public final class RescueCouplerItem extends CouplerAdapterItem implements RescueCouplerItemView {
    public static final String ID = "hexie:crh2a_rescue_coupler";
    public static final String AUTO_MODEL = "hexie:crh2_auto", KNUCKLE_MODEL = "railways:knuckle";
    private static final class GeometryHolder {
        private static final CouplerGeometryView VALUE = HexieBridges.worldBlock().couplerGeometry().load("/data/hexie/crh2a_rescue_coupler_geometry.json");
    }
    public static CouplerGeometryView geometry() { return GeometryHolder.VALUE; }

    public RescueCouplerItem(Properties properties) {
        super(properties, AUTO_MODEL, KNUCKLE_MODEL, -geometry().contact().z,
                new Point3(geometry().contact().x, geometry().contact().y, geometry().contact().z));
    }
}
