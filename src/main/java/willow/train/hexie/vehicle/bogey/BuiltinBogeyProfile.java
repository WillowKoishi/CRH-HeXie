package willow.train.hexie.vehicle.bogey;

import java.util.List;
import willow.train.hexie.vehicle.runtime.BogeyMount;
import willow.train.hexie.vehicle.runtime.BogeyParameters;

import static willow.train.hexie.vehicle.runtime.BogeyMount.Type.*;

/** Built-in gameplay defaults, independent of resource packs and Create. SI units throughout. */
public enum BuiltinBogeyProfile {
    // Geometry (wheel radius, axle positions), mass, traction, brake, power (W), ratio, mount, lateral travel.
    // Game full-brake target: 160 km/h -> 650 m.
    CW2(new BogeyGeometry(.915 / 2, List.of(-1.2, 1.2)),
            24000, 0, 35785.99, 0, 1, BOLSTERLESS, .06),
    // Game full-brake target: 160 km/h -> 700 m.
    PK209P(new BogeyGeometry(.915 / 2, List.of(-1.2, 1.2)),
            24000, 0, 33189.88, 0, 1, BOLSTERED, 0),
    // Game full-brake target: 160 km/h -> 600 m.
    SW220K(new BogeyGeometry(.915 / 2, List.of(-1.25, 1.25)),
            24000, 0, 38814.02, 0, 1, BOLSTERLESS, .06),
    // Game full-brake target: 160 km/h -> 650 m.
    SW160(new BogeyGeometry(.915 / 2, List.of(-1.28, 1.28)),
            24000, 0, 35785.99, 0, 1, BOLSTERLESS, .06),
    // Game full-brake target: 160 km/h -> 850 m.
    DF11G(new BogeyGeometry(1.05 / 2, List.of(-2.0, 0.0, 2.0)),
            69000, 120000, 78291.28, 1520000, 4, BOLSTERLESS, .06),
    // Game full-brake target: 80 km/h -> 400 m.
    QJ(new BogeyGeometry(1.5 / 2, List.of(-3.2, -1.6, 0.0, 1.6, 3.2)),
            119390, 150000, 71753.16, 2191800, 1, BOLSTERED, 0),
    // Game full-brake target: 275 km/h -> 1600 m.
    DT206(new BogeyGeometry(.43, List.of(-1.25, 1.25)),
            24000, 20000, 42531.79, 600000, 4, BOLSTERLESS, .06);

    private final BogeyGeometry geometry;
    private final BogeyParameters operating;
    private final BogeyMount.Type mountType;
    private final double lateralLimit;

    BuiltinBogeyProfile(BogeyGeometry geometry, double mass, double traction, double brake, double powerW,
                       double driveRatio, BogeyMount.Type mountType, double lateralLimit) {
        this.geometry = geometry;
        // Distributed running resistance estimate A+B|v|+Cv² (v in m/s), not measured data.
        // Brakes fitted at 20 Hz to the per-profile game targets above (2-3 km / 10-20 km routes).
        // Relative strengths are tuning choices, not measured model-specific rankings.
        // Low-speed traction caps remain game estimates. Published rated power is used as an
        // effective wheel-power ceiling without additional efficiency loss (game simplification).
        this.operating = new BogeyParameters(mass, traction, brake, mass * .01,
                geometry.wheelRadius(), driveRatio, mass * .0002, mass * .00001, powerW);
        this.mountType = mountType;
        this.lateralLimit = lateralLimit;
    }

    public BogeyGeometry geometry() { return geometry; }
    public BogeyParameters operating() { return operating; }
    public BogeyMount.Type mountType() { return mountType; }
    public double lateralLimit() { return lateralLimit; }
}
