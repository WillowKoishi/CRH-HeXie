package willow.train.hexie.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import willow.train.hexie.entity.HexieSeatEntity;

public class HexieSeatRenderer extends EntityRenderer<HexieSeatEntity> {

    public HexieSeatRenderer(EntityRendererProvider.Context pContext) {
        super(pContext);
    }

    @Override
    public ResourceLocation getTextureLocation(HexieSeatEntity pEntity) {
        return null;
    }

    @Override
    public boolean shouldRender(
            HexieSeatEntity pLivingEntity,
            Frustum pCamera,
            double pCamX,
            double pCamY,
            double pCamZ) {
        return false;
    }

    @Override
    public void render(
            HexieSeatEntity pEntity,
            float pEntityYaw,
            float pPartialTicks,
            PoseStack pMatrixStack,
            MultiBufferSource pBuffer,
            int pPackedLight) {}
}
