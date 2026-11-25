package willow.train.hexie.world.blockinteract;

import java.util.List;
import org.joml.Matrix4f;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.api.interaction.ControlSurface;

/** North-facing operating face of the test bench. Its DMI and panel buttons sit at distinct depths. */
public final class TestCabInteraction {
    private static final ControlSurface SURFACE = new ControlSurface(List.of(
            ControlSurface.button(HexieBridges.cabControl().testCabButton().powerUp(),   .06, .29, 1.41, 1.62, .915),
            ControlSurface.button(HexieBridges.cabControl().testCabButton().powerDown(), .06, .29, 1.20, 1.41, .915),
            ControlSurface.button(HexieBridges.cabControl().testCabButton().brakeUp(),   .71, .94, 1.41, 1.62, .915),
            ControlSurface.button(HexieBridges.cabControl().testCabButton().brakeDown(), .71, .94, 1.20, 1.41, .915),
            ControlSurface.button(HexieBridges.cabControl().testCabButton().reverser(),   .06, .32, 1.02, 1.16, .90),
            ControlSurface.button(HexieBridges.cabControl().testCabButton().mode(),       .37, .63, 1.02, 1.16, .90),
            ControlSurface.button(HexieBridges.cabControl().testCabButton().parking(),    .68, .94, 1.02, 1.16, .90)
    ), ControlSurface.NO_BODY, List.of(new ControlSurface.Mount(HexieBridges.cabControl().ctcs2Panel().instance(),
            new Matrix4f().translation(.5f, 1.52f, .85f))));

    private TestCabInteraction() {}
    public static ControlSurface surface() { return SURFACE; }
}
