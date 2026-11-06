package willow.train.hexie.blocks.emu;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import willow.train.hexie.api.cab.CabSurfaceGeometry;
import willow.train.hexie.api.cab.CabSurfaceState;
import willow.train.hexie.vehicle.control.Crh2CabHandle;
import willow.train.hexie.vehicle.control.Ctcs2Panel;

import static willow.train.hexie.vehicle.control.Crh2CabHandle.BRAKE;
import static willow.train.hexie.vehicle.control.Crh2CabHandle.POWER;
import static willow.train.hexie.vehicle.control.Crh2CabHandle.REVERSER;

/** Authored parts, pivots and handle travel of the non-standard CRH2A desk. */
public final class CRH2CabGeometry {
    private record Mesh(float[] pivot, float[] rest, float[][][] faces) {
        Matrix4f mount() { return CRH2CabGeometry.mount(pivot, rest); }
    }
    private record Marker(float[] front, float[] rotation) {}
    private record Geometry(float[][][] body, float[][][] brakeDome, Map<String, Mesh> handles, Marker dmi) {}

    private static final CabSurfaceGeometry SURFACE = load();
    private CRH2CabGeometry() {}
    public static CabSurfaceGeometry surface() { return SURFACE; }

    private static CabSurfaceGeometry load() {
        try (var in = CRH2CabGeometry.class.getResourceAsStream("/data/hexie/crh2a_nonstandard_driving_console_geometry.json");
             var reader = new InputStreamReader(Objects.requireNonNull(in), StandardCharsets.UTF_8)) {
            var geometry = new Gson().fromJson(reader, Geometry.class);
            var parts = new ArrayList<CabSurfaceGeometry.Part>();
            var power = geometry.handles.get("power");
            parts.add(part(POWER, "power", power.faces, (controls, animation) ->
                    power.mount().rotateX(radians(65f * POWER.notch(controls) / POWER.maxNotch()))));
            var brake = geometry.handles.get("brake");
            parts.add(part(BRAKE, "brake_dome", geometry.brakeDome, (controls, animation) ->
                    swivel(brake, brakePosition(controls, animation))));
            parts.add(part(BRAKE, "brake", brake.faces, (controls, animation) -> {
                float notch = brakePosition(controls, animation);
                float lift = Math.clamp((notch - 8f) / .4f, 0f, 1f) * .028f;
                return swivel(brake, notch).translate(0, lift, 0);
            }));
            var reverser = geometry.handles.get("reverser");
            parts.add(part(REVERSER, "reverser", reverser.faces, (controls, animation) ->
                    reverser.mount().rotateX(radians(-28f * REVERSER.notch(controls)))));
            return new CabSurfaceGeometry(parts, CabSurfaceGeometry.mesh(geometry.body),
                    List.of(new CabSurfaceGeometry.Mount(Ctcs2Panel.INSTANCE, mount(geometry.dmi.front, geometry.dmi.rotation))));
        } catch (IOException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static CabSurfaceGeometry.Part part(Crh2CabHandle handle, String model, float[][][] faces,
                                                CabSurfaceGeometry.Pose pose) {
        var path = ResourceLocation.fromNamespaceAndPath("hexie", "block/crh2a/nonstandard_driving_console/" + model);
        return new CabSurfaceGeometry.Part(handle, path, CabSurfaceGeometry.mesh(faces), pose);
    }

    /** Six ticks of travel from rapid to removal, including the lift before the final swivel. */
    private static float brakePosition(CabSurfaceState controls, CabSurfaceGeometry.Animation animation) {
        int target = BRAKE.notch(controls);
        double travel = animation.travel(BRAKE, target == 9 ? 14 : target);
        return (float) (travel <= 8 ? travel : 8 + (travel - 8) / 6);
    }

    private static Matrix4f swivel(Mesh brake, float notch) {
        float degrees = (60f / 7f) * Math.min(notch, 7f)
                + 20f * Math.clamp(notch - 7f, 0f, 1f)
                + 20f * Math.clamp((notch - 8.4f) / .6f, 0f, 1f);
        return brake.mount().rotateY(radians(degrees));
    }

    private static float radians(float degrees) { return (float) Math.toRadians(degrees); }

    private static Matrix4f mount(float[] origin, float[] euler) {
        return new Matrix4f().translation(origin[0], origin[1], origin[2])
                .rotateXYZ(radians(euler[0]), radians(euler[1]), radians(euler[2]));
    }
}
