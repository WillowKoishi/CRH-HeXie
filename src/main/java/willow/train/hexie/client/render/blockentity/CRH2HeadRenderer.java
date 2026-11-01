package willow.train.hexie.client.render.blockentity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.data.ModelData;
import willow.train.hexie.blockentity.CRH2HeadEntity;
import willow.train.hexie.blocks.locos.head.*;
public final class CRH2HeadRenderer implements BlockEntityRenderer<CRH2HeadEntity> {
    public static final ModelResourceLocation MODEL=ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath("hexie","block/crh2a/head/head"));
    public CRH2HeadRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(CRH2HeadEntity head,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        pose.pushPose();pose.translate(.5,0,.5);
        pose.mulPose(Axis.YP.rotationDegrees(180-head.getBlockState().getValue(CRH2HeadBlock.FACING).toYRot()));
        pose.translate(-.5,0,-.5);
        var mc=Minecraft.getInstance();
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(),buffers.getBuffer(RenderType.cutout()),head.getBlockState(),
                mc.getModelManager().getModel(MODEL),1,1,1,light,overlay,ModelData.EMPTY,RenderType.cutout());
        pose.popPose();
    }
    @Override public boolean shouldRenderOffScreen(CRH2HeadEntity head) { return true; }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(CRH2HeadEntity head) {
        return HeadGeometry.bounds(head.getBlockState().getValue(CRH2HeadBlock.FACING)).move(head.getBlockPos());
    }
}
