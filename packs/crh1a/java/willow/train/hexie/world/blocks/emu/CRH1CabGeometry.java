package willow.train.hexie.world.blocks.emu;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.api.interaction.ControlSurface;
import willow.train.hexie.world.blockinteract.EmuBlockInteract;

/** Authored desk and handle geometry; all coordinates are relative to a north-facing block. */
public final class CRH1CabGeometry {
    private record Mesh(float[] pivot, float[][][] faces) {}
    private record Geometry(float[][][] body, CRH1CabGeometry.Mesh handle, float[] button) {}
    private static final ControlSurface SURFACE = load();
    private CRH1CabGeometry() {}
    public static ControlSurface surface() { return SURFACE; }

    private static ControlSurface load() {
        try (var in = CRH1CabGeometry.class.getResourceAsStream("/data/hexie/crh1a_driving_console_geometry.json");
             var reader = new InputStreamReader(Objects.requireNonNull(in), StandardCharsets.UTF_8)) {
            var geometry = new Gson().fromJson(reader, CRH1CabGeometry.Geometry.class);
            var handle = geometry.handle;
            var handlePart = new ControlSurface.Part(HexieBridges.cabControl().crh1CabHandle().instance(), model("handle"), ControlSurface.mesh(handle.faces),
                    (controls, animation) -> {
                        double position = animation.travel(HexieBridges.cabControl().crh1CabHandle().instance(), HexieBridges.cabControl().crh1CabHandle().instance().notch(controls));
                        double degrees = position < 0 ? -position * 60 / 9 : -position * 50 / 7;
                        return new Matrix4f().translation(handle.pivot[0], handle.pivot[1], handle.pivot[2])
                                .rotateX((float) Math.toRadians(degrees));
                    }, new AABB(handle.pivot[0] - .24, handle.pivot[1] - .24, handle.pivot[2] - .24,
                            handle.pivot[0] + .24, handle.pivot[1] + .24, handle.pivot[2] + .24));
            var at = geometry.button;
            var pick = ControlSurface.button(HexieBridges.cabControl().crh1CabControlsDirectionButton().instance(),
                    at[0] - .023, at[0] + .023, at[1] - .023, at[1] + .023, at[2] + .013);
            var button = new ControlSurface.Part(pick.control(), model("button"), pick.shape(), null);
            // The reserved fourth screen covers texture pixels x=75..80, y=54..58.
            // Stretch the existing 800x600 LCD to those bounds on the sloped panel.
            var screen = new Matrix4f().translation(.6950281f, 1.0593939f, .2692587f)
                    .rotateX((float) -Math.atan(.95 / 2.8)).scale(1.478744f, 1.577312f, 1f);
            return new ControlSurface(List.of(handlePart, button), ControlSurface.mesh(geometry.body),
                    List.of(new ControlSurface.Mount(HexieBridges.cabControl().ctcs2Panel().instance(), screen)));
        } catch (IOException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static ResourceLocation model(String name) {
        return ResourceLocation.fromNamespaceAndPath("hexie", "block/crh1a/driving_console/" + name);
    }

    public static AABB controlBounds(Direction facing) {
        return EmuBlockInteract.ForCRH1CabGeometry.controlBounds(facing);
    }
}
