package willow.train.hexie.client.model; // Made with Blockbench 4.7.4

// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import willow.train.hexie.Main;

/**
 * 模型类，在Renderer中调用，记得在Client/ClientModEventSubscriber中加入注册
 * 在使用前，记得把ResourceLoaction中的namespace和path修改正确，如下例 没有其他事的话其他不用改动
 *
 * @param <T> -
 */
public class CarriageNoSignModel<T extends Entity> extends EntityModel<T> {
    // This layer location should be baked with EntityRendererProvider.Context in the entity
    // renderer and passed into this model's constructor
    public static final ModelLayerLocation CARRIAGE_NO_SIGN_MODEL_LAYER =
            new ModelLayerLocation(
                    ResourceLocation.fromNamespaceAndPath(
                            Main.MOD_ID, "textures/entity/carriage_no_sign_texture.png"),
                    "main");
    private final ModelPart bb_main;

    public CarriageNoSignModel(ModelPart root) {
        this.bb_main = root.getChild("bb_main");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition bb_main =
                partdefinition.addOrReplaceChild(
                        "bb_main",
                        CubeListBuilder.create()
                                .texOffs(0, 0)
                                .addBox(
                                        -1.0F,
                                        -1.0F,
                                        0.0F,
                                        2.0F,
                                        2.0F,
                                        0.0F,
                                        new CubeDeformation(0.0F)),
                        PartPose.offset(0.0F, 24.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 16, 16);
    }

    @Override
    public void setupAnim(
            T entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {}

    public void renderToBuffer(
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int packedLight,
            int packedOverlay,
            float red,
            float green,
            float blue,
            float alpha) {
        bb_main.render(
                poseStack, vertexConsumer, packedLight, packedOverlay, net.minecraft.util.FastColor.ARGB32.color((int) (alpha * 255), (int) (red * 255), (int) (green * 255), (int) (blue * 255)));
    }
    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, int color) {
        renderToBuffer(poseStack, consumer, light, overlay,
                ((color >>> 16) & 255) / 255.0f, ((color >>> 8) & 255) / 255.0f,
                (color & 255) / 255.0f, ((color >>> 24) & 255) / 255.0f);
    }
}
