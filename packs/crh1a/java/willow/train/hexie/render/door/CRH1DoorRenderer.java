package willow.train.hexie.render.door;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.render.DoorRenderBridge.CRH1DoorRendererLightingView;
import willow.train.hexie.bridge.world.BlockEntityViews.CustomRenderedDoorEntityView;
import willow.train.hexie.core.HexieConstants;

/** CRH1-only model parts and two-stage poses, shared by buffered and Flywheel drawing. */
public final class CRH1DoorRenderer {
    private static final PartialModel[][][] MODELS = models();
    public record Lighting(ClientLevel level, Matrix4fc localToWorld) implements CRH1DoorRendererLightingView {}
    private static final ThreadLocal<Lighting> LIGHTING = new ThreadLocal<>();

    private CRH1DoorRenderer() {}

    public static void init() {} // Load every PartialModel before model baking.

    private static PartialModel[][][] models() {
        var result = new PartialModel[2][2][2];
        for (int side = 0; side < 2; side++)
            for (int part = 0; part < 2; part++)
                for (int layer = 0; layer < 2; layer++)
                    result[side][part][layer] = PartialModel.of(HexieConstants.asResource("block/crh1a/doors/crh1a_psg_door"
                            + (part == 0 ? "_frame" : "") + (side == 0 ? "_left" : "_right")
                            + (layer == 0 ? "_body" : "_glass")));
        return result;
    }

    static void drawParts(CustomRenderedDoorEntityView entity, float progress, PoseStack pose,
                          CarriageDoorParts.PartRenderer renderer) {
        var state = entity.asBlockEntity().getBlockState();
        boolean mirrored = state.getValue(DoorBlock.HINGE) == DoorHingeSide.RIGHT;
        var models = MODELS[mirrored ? 1 : 0];
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(switch (state.getValue(DoorBlock.FACING)) {
            case EAST -> -90; case SOUTH -> 180; case WEST -> 90; default -> 0;
        }));
        pose.translate(-.5, 0, -.5);
        renderer.draw(models[0][0], RenderType.cutout(), pose);
        renderer.draw(models[0][1], RenderType.translucent(), pose);
        var offset = HexieBridges.carriageBlock().crh1DoorGeometry().offset(progress, mirrored);
        pose.translate(offset.x, offset.y, offset.z);
        renderer.draw(models[1][0], RenderType.cutout(), pose);
        renderer.draw(models[1][1], RenderType.translucent(), pose);
    }

    public static Lighting lighting() { return LIGHTING.get(); }

    public static Lighting installLighting(ClientLevel level, Matrix4fc localToWorld, BlockPos pos) {
        Lighting previous = LIGHTING.get();
        var matrix = localToWorld == null ? new Matrix4f() : new Matrix4f(localToWorld);
        matrix.translate(pos.getX(), pos.getY(), pos.getZ());
        LIGHTING.set(new Lighting(level, matrix));
        return previous;
    }

    public static void restoreLighting(Lighting previous) {
        if (previous == null) LIGHTING.remove();
        else LIGHTING.set(previous);
    }
}
