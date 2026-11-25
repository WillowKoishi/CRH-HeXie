package willow.train.hexie.render.bogey;

// split from willow/train/hexie/client/render/bogey/BogeySuspensionModels.java

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;
import willow.train.hexie.bridge.render.BogeyRenderBridge.BogeySuspensionModelsModelView;
import willow.train.hexie.core.HexieConstants;

/** Parts extracted from the original bogey meshes, in each frame's original export coordinates. */
public final class BogeySuspensionModels {
    enum Attachment { BODY, FRAME, TRANSLATION, LINK, SLEEVE, BODY_SLEEVE, PISTON }
    record Part(PartialModel model, Attachment attachment, Vector3f frameAnchor, Vector3f bodyAnchor,
                        float pistonReach) {}
    public static final class Model implements BogeySuspensionModelsModelView {
        private final String directory;
        final double frameOffset;
        final List<Part> parts = new ArrayList<>();
        private Model(String directory, double frameOffset, boolean airSpring) {
            this.directory = directory;
            this.frameOffset = frameOffset;
            if (airSpring) translated("air_spring_upper", new Vector3f());
        }
        private Model attached(String name, Attachment attachment) {
            parts.add(new Part(part(name), attachment, null, null, 0));
            return this;
        }
        private Model translated(String name, Vector3f bodyAnchor) {
            parts.add(new Part(part(name), Attachment.TRANSLATION, null, bodyAnchor, 0));
            return this;
        }
        private Model link(String name, Vector3f frame, Vector3f body, boolean stretch) {
            parts.add(new Part(part(name), stretch ? Attachment.LINK : Attachment.SLEEVE, frame, body, 0));
            return this;
        }
        private Model bodySleeve(String name, Vector3f frame, Vector3f body) {
            parts.add(new Part(part(name), Attachment.BODY_SLEEVE, frame, body, 0));
            return this;
        }
        private Model piston(String name, Vector3f frame, Vector3f body, float restReach) {
            parts.add(new Part(part(name), Attachment.PISTON, frame, body, restReach));
            return this;
        }
        private PartialModel part(String name) {
            return PartialModel.of(HexieConstants.asResource("block/bogey/" + directory + "/" + name));
        }
    }

    public static final Model CW2 = longitudinal("hx_standard", 0, true, 1.340625f, .921875f, .834375f, .184375f, .184375f, false);
    public static final Model SW160 = longitudinal("sw160", .264, true, 1.4125f, .56875f, .326492915f, -.484375f, -.484375f, true);
    public static final Model PK209P = longitudinal("pk209p", .91, false, 1.5f, .225f, .496875f, -.3375f, -.33125f, true);
    public static final Model SW220K = sw220k();
    public static final Model DT206 = emu("dt206");
    public static final Model SKTB200 = emu("sktb200");
    public static final Model CRH1A_MOTOR = crh1a("crh1a_motor", true);
    public static final Model CRH1A_TRAILER = crh1a("crh1a_trailer", false);
    public static final Model DF11G = df11g();

    private BogeySuspensionModels() {}
    public static void init() { /* Initialize PartialModels before model baking. */ }

    public static Model longitudinal(String directory, double offset, boolean airSpring,
                                      float x, float y, float frameZ, float leftBodyZ, float rightBodyZ, boolean opposite) {
        var model = new Model(directory, offset, airSpring).attached("bolster", Attachment.BODY);
        for (int side : new int[] {-1, 1}) {
            String name = side < 0 ? "left" : "right";
            float zSign = opposite ? side : 1;
            float bodyZ = side < 0 ? leftBodyZ : rightBodyZ;
            model.link("longitudinal_link_" + name, new Vector3f(side * x, y, frameZ * zSign),
                    new Vector3f(side * x, y, bodyZ * zSign), true)
                    .attached("longitudinal_link_mount_" + name, Attachment.BODY);
        }
        return model;
    }

    public static Model emu(String directory) {
        var model = new Model(directory, .7726 + 5.0 / 16, true);
        for (int side : new int[] {-1, 1}) {
            String name = side < 0 ? "left" : "right";
            var frame = new Vector3f(side * 1.3625f, -.33125f, -.0625f);
            var body = new Vector3f(side * 1.3625f, -.33125f, -.6875f);
            model.link("rod_" + name + "_shaft", frame, body, true)
                    .link("rod_" + name + "_sleeve", frame, body, false)
                    .attached("rod_" + name + "_upper_mount", Attachment.BODY);
        }
        return model;
    }

    public static Model crh1a(String directory, boolean motor) {
        // The source frame's origin is the axle centre; 7.328/16 tread height puts it on the rail.
        var model = new Model(directory, .8006, false)
                .attached("bolster", Attachment.BODY)
                .attached("primary_suspension", Attachment.FRAME)
                .attached("brakes", Attachment.FRAME)
                .attached("anti_roll_bar", Attachment.FRAME)
                .link("traction_rod", crh1aTractionAnchor(.5f), crh1aTractionAnchor(-10.8f), true);
        if (motor) model.attached("motors", Attachment.FRAME);
        for (int side : new int[] {-1, 1}) {
            String name = side < 0 ? "left" : "right";
            model.translated("air_spring_" + name,
                            new Vector3f(side * 15.15f / 16, 7.5f / 16, 0))
                    .link("vertical_damper_" + name,
                            new Vector3f(side * 9.2f / 16, -.1f / 16, -4.5f / 16),
                            new Vector3f(side * 9.2f / 16, 8.3f / 16, -4.5f / 16), true)
                    .link("anti_roll_link_" + name,
                            new Vector3f(side * 9.2f / 16, -2.8f / 16, 3.6f / 16),
                            new Vector3f(side * 9.2f / 16, 8.3f / 16, 3.6f / 16), true);
            var frame = crh1aDamperAnchor(side, 9.2f);
            var body = crh1aDamperAnchor(side, -2.8f);
            // Source piston tip is 6/16 from the frame pivot, overlapping the sleeve by .6/16.
            model.piston("yaw_damper_" + name + "_shaft", frame, body, 6f / 16)
                    .bodySleeve("yaw_damper_" + name + "_sleeve", frame, body)
                    .attached("yaw_damper_" + name + "_upper_mount", Attachment.BODY);
        }
        return model;
    }

    private static Vector3f crh1aDamperAnchor(int side, float z) {
        // Bake the source damper group's 5-degree X rotation around (side*21.3, 1.8, .5).
        float sin = (float) Math.sin(Math.toRadians(5)), cos = (float) Math.cos(Math.toRadians(5));
        return new Vector3f(side * 21.6f / 16, (1.8f - (z - .5f) * sin) / 16,
                (.5f + (z - .5f) * cos) / 16);
    }

    private static Vector3f crh1aTractionAnchor(float z) {
        float sin = (float) Math.sin(Math.toRadians(10)), cos = (float) Math.cos(Math.toRadians(10));
        return new Vector3f(0, (.7f + cos - z * sin) / 16, (5.6f + sin + z * cos) / 16);
    }

    public static Model sw220k() {
        var model = new Model("sw220k", .635, true);
        for (int side : new int[] {-1, 1}) {
            String name = side < 0 ? "left" : "right";
            var frame = new Vector3f(side * 1.3734375f, .2696875f, .0046875f);
            var body = new Vector3f(side * 1.3734375f, .2696875f, .81796875f);
            model.link("traction_rod_" + name, frame, body, true)
                    .attached("traction_frame_mount_" + name, Attachment.FRAME);
        }
        return model;
    }

    public static Model df11g() {
        var model = new Model("df11g", .375, false);
        for (int side : new int[] {-1, 1}) {
            String name = side < 0 ? "left" : "right";
            model.link("suspension_link_" + name + "_front",
                    new Vector3f(side * 1.34921875f, .5987134742f, .0195621395f),
                    new Vector3f(side * 1.34921875f, 1.157427795f, .2894340427f), true);
            model.link("suspension_link_" + name + "_rear",
                    new Vector3f(side * 1.34921875f, .5987134742f, -.0195621395f),
                    new Vector3f(side * 1.34921875f, 1.153840138f, -.2980954134f), true);
            model.link("traction_rod_" + name,
                    new Vector3f(side * 1.3734375f, .5734375f, -.0140625f),
                    new Vector3f(side * 1.3734375f, .5734375f, 1.0609375f), true);
            model.attached("traction_top_" + name, Attachment.BODY)
                    .attached("suspension_frame_mount_" + name, Attachment.FRAME);
        }
        return model;
    }

}
