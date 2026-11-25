package willow.train.hexie.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class HexieSeatRenderer extends EntityRenderer<SeatEntity> {

    public HexieSeatRenderer(EntityRendererProvider.Context pContext) {
        super(pContext);
    }

    @Override
    public ResourceLocation getTextureLocation(SeatEntity pEntity) {
        return null;
    }

    @Override
    public boolean shouldRender(
            SeatEntity pLivingEntity,
            Frustum pCamera,
            double pCamX,
            double pCamY,
            double pCamZ) {
        return false;
    }

    @Override
    public void render(
            SeatEntity pEntity,
            float pEntityYaw,
            float pPartialTicks,
            PoseStack pMatrixStack,
            MultiBufferSource pBuffer,
            int pPackedLight) {}
}
