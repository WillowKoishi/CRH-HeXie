package willow.train.hexie.client.render.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import willow.train.hexie.blockentity.CRH2HeadEntity;
import willow.train.hexie.blocks.emu.head.CRH2HeadBlock;
import willow.train.hexie.blocks.emu.head.HeadGeometry;
import willow.train.hexie.client.sign.SignTextRenderer;

/** Decals on the authored lower side panels, below the blue stripe and ahead of the cab doors. */
public final class HeadNumberRenderer implements BlockEntityRenderer<CRH2HeadEntity> {
    public static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath("hexie", "head_number");
    // Annotated reference: number/CRH width = 171/221, versus 331/507 in the prior game view.
    // Raise the ink above the marked door-bottom line and anchor its door-side edge at the R/H gap.
    public static final float NUMBER_WIDTH = .88f;
    public static final float NUMBER_HEIGHT = .132f;
    public static final float NUMBER_BOTTOM = 1.15f;
    public static final float NUMBER_DOOR_EDGE_Z = -2.4f;

    /** Authored lower side panel triangles (head.obj), rather than the higher blue-stripe plane. */
    private static final double[][][] PANELS = {
        {{2.15,1.3625,-3},{2.1875,1.3625,-2},{2.1875,1,-2}},
        {{2.15,1.3625,-3},{2.1875,1,-2},{2.1875,1,-3}},
        {{2.109375,1.33125,-4},{2.15,1.3625,-3},{2.1875,1,-3}},
        {{2.109375,1.33125,-4},{2.1875,1,-3},{2.1875,1,-4}}
    };
    public static double surfaceX(double y, double z) {
        for (var t : PANELS) {
            var a=t[0]; var b=t[1]; var c=t[2];
            double d=(b[1]-a[1])*(c[2]-a[2])-(c[1]-a[1])*(b[2]-a[2]);
            double u=((y-a[1])*(c[2]-a[2])-(c[1]-a[1])*(z-a[2]))/d;
            double v=((b[1]-a[1])*(z-a[2])-(y-a[1])*(b[2]-a[2]))/d;
            if (u>=-1e-6 && v>=-1e-6 && u+v<=1.000001)
                return a[0]+u*(b[0]-a[0])+v*(c[0]-a[0]);
        }
        return 2.1875; // Transparent raster padding may extend just below the panel edge.
    }
    public HeadNumberRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(CRH2HeadEntity head, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
        if (head.number().isBlank()) return;
        pose.pushPose();
        pose.translate(.5, 0, .5);
        float rotation = switch (head.getBlockState().getValue(CRH2HeadBlock.FACING)) {
            case EAST -> -90; case SOUTH -> 180; case WEST -> 90; default -> 0;
        };
        pose.mulPose(Axis.YP.rotationDegrees(rotation));
        pose.translate(-.5, 0, -.5);
        float inkHeight = SignTextRenderer.height(FONT, head.number(), NUMBER_HEIGHT, NUMBER_WIDTH);
        float inkWidth = SignTextRenderer.width(FONT, head.number(), NUMBER_HEIGHT, NUMBER_WIDTH);
        // Fix the edge nearest the door, rather than shifting it when a shorter number is entered.
        float centerZ = NUMBER_DOOR_EDGE_Z - inkWidth / 2;
        for (int side : new int[]{-1, 1}) {
            SignTextRenderer.drawLine(FONT, head.number(), pose, buffers, 0, inkHeight,
                    NUMBER_HEIGHT, NUMBER_WIDTH, 0xFF171717, light, SignTextRenderer.Alignment.CENTER, true,
                    (x, y) -> {
                        double z = centerZ - side * x, worldY = NUMBER_BOTTOM + y;
                        double rightX = surfaceX(worldY, z) + .002;
                        return new net.minecraft.world.phys.Vec3(side > 0 ? rightX : 1 - rightX, worldY, z);
                    });
        }
        pose.popPose();
    }
    @Override public AABB getRenderBoundingBox(CRH2HeadEntity head) {
        return HeadGeometry.bounds(head.getBlockState().getValue(CRH2HeadBlock.FACING)).move(head.getBlockPos());
    }
    @Override public boolean shouldRenderOffScreen(CRH2HeadEntity head) { return true; }
}
