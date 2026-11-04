package willow.train.hexie.client.render.track;

import dev.engine_room.flywheel.api.instance.InstanceHandle;
import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.layout.FloatRepr;
import dev.engine_room.flywheel.api.layout.LayoutBuilder;
import dev.engine_room.flywheel.api.material.CardinalLightingMode;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.AbstractInstance;
import dev.engine_room.flywheel.lib.instance.SimpleInstanceType;
import dev.engine_room.flywheel.lib.material.LightShaders;
import dev.engine_room.flywheel.lib.material.Materials;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.material.SimpleMaterialShaders;
import dev.engine_room.flywheel.lib.material.StandardMaterialShaders;
import org.lwjgl.system.MemoryUtil;
import willow.train.hexie.Main;

/** The model holds static bed geometry. Only its placement, tint and painted face are instance data. */
public final class CurveBedInstance extends AbstractInstance {
    private static final Material MATERIAL = SimpleMaterial.builderOf(Materials.CUTOUT_MIPPED_BLOCK)
            .shaders(new SimpleMaterialShaders(StandardMaterialShaders.DEFAULT.vertexSource(),
                    Main.asResource("material/curve_bed.frag")))
            .light(LightShaders.SMOOTH).cardinalLightingMode(CardinalLightingMode.CHUNK).useOverlay(false)
            .build();
    private static final InstanceType<CurveBedInstance> TYPE = SimpleInstanceType.builder(CurveBedInstance::new)
            .layout(LayoutBuilder.create()
                    .vector("positionSpan", FloatRepr.FLOAT, 4)
                    .vector("faceRow0", FloatRepr.FLOAT, 4)
                    .vector("faceRow1", FloatRepr.FLOAT, 4)
                    .vector("tint", FloatRepr.NORMALIZED_UNSIGNED_BYTE, 4)
                    .build())
            .writer(CurveBedInstance::write)
            .vertexShader(Main.asResource("instance/curve_bed.vert"))
            .cullShader(Main.asResource("instance/cull/curve_bed.glsl"))
            .build();

    private int x, y, z;
    private boolean repeating;
    private BallastModels.Material texture;

    private CurveBedInstance(InstanceType<CurveBedInstance> type, InstanceHandle handle) {
        super(type, handle);
    }

    public static Material material() { return MATERIAL; }

    /** Coordinates are relative to the visualization context's render origin. */
    public static CurveBedInstance create(VisualizationContext ctx, Model model, int x, int y, int z,
            BallastModels.Material texture, boolean repeating) {
        var instance = ctx.instancerProvider().instancer(TYPE, model).createInstance();
        instance.x = x;
        instance.y = y;
        instance.z = z;
        instance.texture = texture;
        instance.repeating = repeating;
        instance.setChanged();
        return instance;
    }

    private static void write(long ptr, CurveBedInstance bed) {
        var texture = bed.texture;
        MemoryUtil.memPutFloat(ptr, bed.x);
        MemoryUtil.memPutFloat(ptr + 4, bed.y);
        MemoryUtil.memPutFloat(ptr + 8, bed.z);
        MemoryUtil.memPutFloat(ptr + 12, bed.repeating ? texture.mipSpan() : -texture.mipSpan());
        for (int v = 0; v < 2; v++) for (int u = 0; u < 2; u++) {
            long corner = ptr + 16 + v * 16 + u * 8;
            MemoryUtil.memPutFloat(corner, texture.atlasU(u, v));
            MemoryUtil.memPutFloat(corner + 4, texture.atlasV(u, v));
        }
        int rgb = texture.color();
        MemoryUtil.memPutByte(ptr + 48, (byte) (rgb >> 16));
        MemoryUtil.memPutByte(ptr + 49, (byte) (rgb >> 8));
        MemoryUtil.memPutByte(ptr + 50, (byte) rgb);
        MemoryUtil.memPutByte(ptr + 51, (byte) 255);
    }
}
