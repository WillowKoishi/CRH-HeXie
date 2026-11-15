package willow.train.hexie.blocks.emu.crh1a;

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
import willow.train.hexie.api.interaction.ControlSurface;
import willow.train.hexie.vehicle.control.Crh1CabControls;
import willow.train.hexie.vehicle.control.Crh1CabHandle;
import willow.train.hexie.vehicle.control.Ctcs2Panel;

/** Authored desk and handle geometry; all coordinates are relative to a north-facing block. */
public final class CRH1CabGeometry {
    private record Mesh(float[] pivot, float[][][] faces) {}
    private record Geometry(float[][][] body, Mesh handle, float[] button) {}
    private static final ControlSurface SURFACE = load();
    private CRH1CabGeometry() {}
    public static ControlSurface surface() { return SURFACE; }

    private static ControlSurface load() {
        try (var in = CRH1CabGeometry.class.getResourceAsStream("/data/hexie/crh1a_driving_console_geometry.json");
             var reader = new InputStreamReader(Objects.requireNonNull(in), StandardCharsets.UTF_8)) {
            var geometry = new Gson().fromJson(reader, Geometry.class);
            var handle = geometry.handle;
            var handlePart = new ControlSurface.Part(Crh1CabHandle.INSTANCE, model("handle"), ControlSurface.mesh(handle.faces),
                    (controls, animation) -> {
                        double position = animation.travel(Crh1CabHandle.INSTANCE, Crh1CabHandle.INSTANCE.notch(controls));
                        double degrees = position < 0 ? -position * 60 / 9 : -position * 50 / 7;
                        return new Matrix4f().translation(handle.pivot[0], handle.pivot[1], handle.pivot[2])
                                .rotateX((float) Math.toRadians(degrees));
                    }, new AABB(handle.pivot[0] - .24, handle.pivot[1] - .24, handle.pivot[2] - .24,
                            handle.pivot[0] + .24, handle.pivot[1] + .24, handle.pivot[2] + .24));
            var at = geometry.button;
            var pick = ControlSurface.button(Crh1CabControls.DirectionButton.INSTANCE,
                    at[0] - .023, at[0] + .023, at[1] - .023, at[1] + .023, at[2] + .013);
            var button = new ControlSurface.Part(pick.control(), model("button"), pick.shape(), null);
            // The reserved fourth screen covers texture pixels x=75..80, y=54..58.
            // Stretch the existing 800x600 LCD to those bounds on the sloped panel.
            var screen = new Matrix4f().translation(.6950281f, 1.0593939f, .2692587f)
                    .rotateX((float) -Math.atan(.95 / 2.8)).scale(1.478744f, 1.577312f, 1f);
            return new ControlSurface(List.of(handlePart, button), ControlSurface.mesh(geometry.body),
                    List.of(new ControlSurface.Mount(Ctcs2Panel.INSTANCE, screen)));
        } catch (IOException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static ResourceLocation model(String name) {
        return ResourceLocation.fromNamespaceAndPath("hexie", "block/crh1a/driving_console/" + name);
    }

    public static AABB controlBounds(Direction facing) {
        var north = new AABB(.43, .63, .08, 1.12, 1.42, .96);
        return switch (facing) {
            case EAST -> new AABB(1 - north.maxZ, north.minY, north.minX, 1 - north.minZ, north.maxY, north.maxX);
            case SOUTH -> new AABB(1 - north.maxX, north.minY, 1 - north.maxZ, 1 - north.minX, north.maxY, 1 - north.minZ);
            case WEST -> new AABB(north.minZ, north.minY, 1 - north.maxX, north.maxZ, north.maxY, 1 - north.minX);
            default -> north;
        };
    }
}
