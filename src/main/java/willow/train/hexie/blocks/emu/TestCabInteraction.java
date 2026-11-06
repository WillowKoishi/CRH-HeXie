package willow.train.hexie.blocks.emu;

import java.util.List;
import org.joml.Matrix4f;
import willow.train.hexie.api.cab.CabSurfaceGeometry;
import willow.train.hexie.vehicle.control.Ctcs2Panel;

import static willow.train.hexie.api.cab.CabSurfaceGeometry.button;
import static willow.train.hexie.vehicle.control.TestCabButton.*;

/** North-facing operating face of the test bench. Its DMI and panel buttons sit at distinct depths. */
public final class TestCabInteraction {
    private static final CabSurfaceGeometry SURFACE = new CabSurfaceGeometry(List.of(
            button(POWER_UP,   .06, .29, 1.41, 1.62, .915),
            button(POWER_DOWN, .06, .29, 1.20, 1.41, .915),
            button(BRAKE_UP,   .71, .94, 1.41, 1.62, .915),
            button(BRAKE_DOWN, .71, .94, 1.20, 1.41, .915),
            button(REVERSER,   .06, .32, 1.02, 1.16, .90),
            button(MODE,       .37, .63, 1.02, 1.16, .90),
            button(PARKING,    .68, .94, 1.02, 1.16, .90)
    ), CabSurfaceGeometry.NO_BODY, List.of(new CabSurfaceGeometry.Mount(Ctcs2Panel.INSTANCE,
            new Matrix4f().translation(.5f, 1.52f, .85f))));

    private TestCabInteraction() {}
    public static CabSurfaceGeometry surface() { return SURFACE; }
}
