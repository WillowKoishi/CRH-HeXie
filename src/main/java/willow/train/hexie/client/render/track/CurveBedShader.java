package willow.train.hexie.client.render.track;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import willow.train.hexie.Main;

import java.io.IOException;

/** Raw curve UVs remain continuous until the fragment shader chooses a texel on the painted face. */
public final class CurveBedShader {
    private static ShaderInstance program;
    private static BallastModels.Material previousMaterial;
    private static boolean previousRepeat;

    private CurveBedShader() {}

    public static void register() {
        Main.bus.addListener(CurveBedShader::load);
    }

    static ShaderInstance instance() { return program; }

    private static void load(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Main.asResource("curve_bed"),
                    DefaultVertexFormat.BLOCK), loaded -> {
                program = loaded;
                previousMaterial = null;
            });
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load curved track bed shader", ex);
        }
    }

    public static ShaderInstance shader(BallastModels.Material material, boolean repeating) {
        if (previousMaterial != material) {
            program.safeGetUniform("FaceRow0").set(material.atlasU(0, 0), material.atlasV(0, 0),
                    material.atlasU(1, 0), material.atlasV(1, 0));
            program.safeGetUniform("FaceRow1").set(material.atlasU(0, 1), material.atlasV(0, 1),
                    material.atlasU(1, 1), material.atlasV(1, 1));
            program.safeGetUniform("FaceMipSpan").set(material.mipSpan());
        }
        if (previousMaterial == null || previousRepeat != repeating)
            program.safeGetUniform("RepeatFace").set(repeating ? 1 : 0);
        previousMaterial = material;
        previousRepeat = repeating;
        return program;
    }
}
