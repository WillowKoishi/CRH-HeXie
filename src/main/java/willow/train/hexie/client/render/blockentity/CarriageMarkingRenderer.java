package willow.train.hexie.client.render.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import willow.train.hexie.blockentity.CRH2PartEntity;
import willow.train.hexie.blocks.locos.crh2a.CRH2PanelBlock;
import willow.train.hexie.client.sign.SignLettering;
import willow.train.hexie.client.sign.SignTextRenderer;

/** Number + two description lines, anchored by the description's outer edge inside the panel. */
public final class CarriageMarkingRenderer implements BlockEntityRenderer<CRH2PartEntity> {
    private static final ResourceLocation CHINESE_FONT = ResourceLocation.fromNamespaceAndPath("hexie", "carriage_marking_chinese");
    private static final float GAP=.085f, DESCRIPTION_WIDTH=.64f;
    private static final double EDGE_INSET=2/16d;
    public CarriageMarkingRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(CRH2PartEntity entity,float partialTick,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        var state=entity.getBlockState();
        if(!(state.getBlock() instanceof willow.train.hexie.blocks.parts.VehicleMarkingSurface block)
                ||!block.markingRoot(entity.getLevel(),entity.getBlockPos(),state).equals(entity.getBlockPos()))return;
        var fields=entity.fields();
        if(java.util.Arrays.stream(fields).allMatch(String::isBlank)&&entity.harmony(0)==0&&entity.harmony(1)==0)return;
        var localBounds=block.markingBounds(state);
        pose.pushPose();
        pose.translate(.5,0,.5);
        pose.mulPose(Axis.YP.rotationDegrees(switch(block.markingFacing(state)){
            case EAST -> -90;case SOUTH -> 180;case WEST -> 90;default -> 0;
        }));
        pose.translate(-.5,0,-.5);
        for(int slot=0;slot<block.markingSlots(state);slot++) {
            if(entity.harmony(slot)==0)continue;
            boolean right=entity.harmony(slot)==2;
            var layout=willow.train.hexie.blocks.locos.crh2a.HarmonyMarking.layout(localBounds.minX+slot,localBounds.maxY,right);
            SignTextRenderer.drawDecal(ResourceLocation.fromNamespaceAndPath("hexie",
                    "textures/logo/crh-chinarailwayhighspeed"+(right ? "_r" : "")+".png"),pose,buffers,
                    layout.left(),layout.top(),layout.width(),layout.height(),light,
                    (x,y)->new net.minecraft.world.phys.Vec3(x,y,
                            block.markingDepth(y)+.002));
        }
        // Panels stand on the floor; the head's origin is the floor block itself.
        double cellLeft=localBounds.minX+(block.markingSlots(state)>1 ? entity.markingSlot() : 0);
        double anchor=entity.descriptionLeft() ? cellLeft+EDGE_INSET : cellLeft+1-EDGE_INSET;
        pose.translate(anchor,HeadNumberRenderer.NUMBER_BOTTOM-1,block.markingDepth(1)+.002);
        drawMarking(fields,entity.descriptionLeft(),pose,buffers,light);
        pose.popPose();
    }
    public static void drawMarking(String[] fields,boolean descriptionLeft,PoseStack pose,MultiBufferSource buffers,int light) {
        float height=HeadNumberRenderer.NUMBER_HEIGHT,maxWidth=HeadNumberRenderer.NUMBER_WIDTH;
        float numberWidth=SignTextRenderer.width(HeadNumberRenderer.FONT,fields[0],height,maxWidth);
        float chineseWidth=SignTextRenderer.width(CHINESE_FONT,fields[1],.092f,DESCRIPTION_WIDTH);
        float englishWidth=SignTextRenderer.width(SignLettering.VEHICLE_PINYIN,fields[2],.044f,DESCRIPTION_WIDTH);
        float descriptionWidth=Math.max(chineseWidth,englishWidth);
        float descriptionCenter=descriptionLeft ? descriptionWidth/2 : -descriptionWidth/2;
        float numberLeft=descriptionLeft ? descriptionWidth+GAP : -descriptionWidth-GAP-numberWidth;
        line(HeadNumberRenderer.FONT,fields[0],numberLeft,0,height,maxWidth,SignTextRenderer.Alignment.LEFT,pose,buffers,light);
        line(CHINESE_FONT,fields[1],descriptionCenter,.06f,.092f,DESCRIPTION_WIDTH,SignTextRenderer.Alignment.CENTER,pose,buffers,light);
        line(SignLettering.VEHICLE_PINYIN,fields[2],descriptionCenter,0,.044f,DESCRIPTION_WIDTH,SignTextRenderer.Alignment.CENTER,pose,buffers,light);
    }
    private static void line(ResourceLocation font,String text,float x,float bottom,float height,float maxWidth,
                             SignTextRenderer.Alignment alignment,PoseStack pose,MultiBufferSource buffers,int light) {
        float inkHeight=SignTextRenderer.height(font,text,height,maxWidth);
        SignTextRenderer.drawLine(font,text,pose,buffers,x,bottom+inkHeight,height,maxWidth,
                0xFF171717,light,alignment,true,SignTextRenderer.FLAT_SURFACE);
    }
    @Override public AABB getRenderBoundingBox(CRH2PartEntity entity) {
        if(entity.getBlockState().getBlock() instanceof willow.train.hexie.blocks.parts.VehicleMarkingSurface panel)
            return panel.markingRenderBounds(entity.getBlockState()).inflate(3,.2,3).move(entity.getBlockPos());
        return new AABB(entity.getBlockPos());
    }
    @Override public boolean shouldRenderOffScreen(CRH2PartEntity entity) { return true; }
}
