package willow.train.hexie.vehicle.bogey;

import java.util.List;

/** Existing HeXie model dimensions. These are not mass, traction or suspension defaults. */
public final class HexieBogeyGeometries {
    public static final BogeyGeometry DT206 = new BogeyGeometry(0.43, List.of(-1.25, 1.25));
    public static final BogeyGeometry CW2 = new BogeyGeometry(0.915 / 2, List.of(-1.2, 1.2));
    public static final BogeyGeometry PK209P = new BogeyGeometry(0.915 / 2, List.of(-1.2, 1.2));
    public static final BogeyGeometry SW220K = new BogeyGeometry(0.915 / 2, List.of(-1.25, 1.25));
    public static final BogeyGeometry SW160 = new BogeyGeometry(0.915 / 2, List.of(-1.28, 1.28));
    public static final BogeyGeometry DF11G = new BogeyGeometry(1.05 / 2, List.of(-2.0, 0.0, 2.0));
    public static final BogeyGeometry QJ = new BogeyGeometry(1.5 / 2, List.of(-3.2, -1.6, 0.0, 1.6, 3.2));

    private HexieBogeyGeometries() {}
}
