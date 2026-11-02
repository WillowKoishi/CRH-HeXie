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

/** Number + two description lines, anchored to the digit adjacent to the description. */
public final class CarriageMarkingRenderer implements BlockEntityRenderer<CRH2PartEntity> {
    private static final ResourceLocation CHINESE_FONT = ResourceLocation.fromNamespaceAndPath("hexie", "carriage_marking_chinese");
    private static final float GAP=.085f, DESCRIPTION_WIDTH=.64f;
    public CarriageMarkingRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(CRH2PartEntity entity,float partialTick,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        var state=entity.getBlockState();
        if(!(state.getBlock() instanceof CRH2PanelBlock block)||state.getValue(CRH2PanelBlock.PART)!=0)return;
        var fields=entity.fields();
        if(java.util.Arrays.stream(fields).allMatch(String::isBlank))return;
        var localBounds=block.placementBounds(state.setValue(CRH2PanelBlock.FACING,Direction.NORTH));
        pose.pushPose();
        pose.translate(.5,0,.5);
        pose.mulPose(Axis.YP.rotationDegrees(switch(state.getValue(CRH2PanelBlock.FACING)){
            case EAST -> -90;case SOUTH -> 180;case WEST -> 90;default -> 0;
        }));
        pose.translate(-.5,0,-.5);
        // Panels stand on the floor; the head's origin is the floor block itself.
        pose.translate((localBounds.minX+localBounds.maxX)/2,HeadNumberRenderer.NUMBER_BOTTOM-1,block.markingDepth()+.002);
        drawMarking(fields,entity.descriptionLeft(),pose,buffers,light);
        pose.popPose();
    }
    public static void drawMarking(String[] fields,boolean descriptionLeft,PoseStack pose,MultiBufferSource buffers,int light) {
        float height=HeadNumberRenderer.NUMBER_HEIGHT,maxWidth=HeadNumberRenderer.NUMBER_WIDTH;
        float numberWidth=SignTextRenderer.width(HeadNumberRenderer.FONT,fields[0],height,maxWidth);
        float digit=SignTextRenderer.digitCenter(HeadNumberRenderer.FONT,fields[0],height,maxWidth,descriptionLeft);
        float numberLeft=-digit;
        line(HeadNumberRenderer.FONT,fields[0],numberLeft,0,height,maxWidth,SignTextRenderer.Alignment.LEFT,pose,buffers,light);
        float chineseWidth=SignTextRenderer.width(CHINESE_FONT,fields[1],.092f,DESCRIPTION_WIDTH);
        float englishWidth=SignTextRenderer.width(SignLettering.VEHICLE_PINYIN,fields[2],.044f,DESCRIPTION_WIDTH);
        float descriptionWidth=Math.max(chineseWidth,englishWidth);
        float descriptionCenter=descriptionLeft ? numberLeft-GAP-descriptionWidth/2
                : numberLeft+numberWidth+GAP+descriptionWidth/2;
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
        if(entity.getBlockState().getBlock() instanceof CRH2PanelBlock panel)
            return panel.placementBounds(entity.getBlockState()).inflate(1.5,0,1.5).move(entity.getBlockPos());
        return new AABB(entity.getBlockPos());
    }
    @Override public boolean shouldRenderOffScreen(CRH2PartEntity entity) { return true; }
}
