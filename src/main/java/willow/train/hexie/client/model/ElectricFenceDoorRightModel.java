package willow.train.hexie.client.model; // Made with Blockbench 4.8.1

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

public class ElectricFenceDoorRightModel<T extends Entity> extends EntityModel<T> {
    // This layer location should be baked with EntityRendererProvider.Context in the entity
    // renderer and passed into this model's constructor
    public static final ModelLayerLocation RIGHT_LAYER =
            new ModelLayerLocation(
                    ResourceLocation.fromNamespaceAndPath(
                            Main.MOD_ID,
                            "textures/block/electric_fence/electric_fence_door_texture.png"),
                    "main");
    private final ModelPart bone;

    public ElectricFenceDoorRightModel(ModelPart root) {
        this.bone = root.getChild("bone");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition bone =
                partdefinition.addOrReplaceChild(
                        "bone",
                        CubeListBuilder.create()
                                .texOffs(30, 2)
                                .addBox(
                                        0.5F,
                                        -2.0F,
                                        2.5F,
                                        15.0F,
                                        0.5F,
                                        1.0F,
                                        new CubeDeformation(0.0F))
                                .texOffs(0, 110)
                                .addBox(
                                        0.5F,
                                        -19.5F,
                                        2.9F,
                                        15.0F,
                                        17.5F,
                                        0.2F,
                                        new CubeDeformation(0.0F))
                                .texOffs(0, 29)
                                .addBox(
                                        0.25F,
                                        -20.0F,
                                        2.5F,
                                        17.5F,
                                        0.5F,
                                        1.0F,
                                        new CubeDeformation(0.0F))
                                .texOffs(0, 18)
                                .addBox(
                                        0.0F,
                                        -5.0F,
                                        3.5F,
                                        19.0F,
                                        1.0F,
                                        2.0F,
                                        new CubeDeformation(0.0F))
                                .texOffs(0, 21)
                                .addBox(
                                        0.0F,
                                        -6.0F,
                                        3.5F,
                                        19.0F,
                                        1.0F,
                                        1.475F,
                                        new CubeDeformation(0.0F)),
                        PartPose.offset(-8.0F, 24.0F, -9.0F));

        PartDefinition cube_r1 =
                bone.addOrReplaceChild(
                        "cube_r1",
                        CubeListBuilder.create()
                                .texOffs(0, 23)
                                .addBox(
                                        -1.0F,
                                        -0.525F,
                                        -0.275F,
                                        19.0F,
                                        1.125F,
                                        0.5F,
                                        new CubeDeformation(0.0F)),
                        PartPose.offsetAndRotation(1.0F, -5.425F, 5.025F, 0.48F, 0.0F, 0.0F));

        PartDefinition cube_r2 =
                bone.addOrReplaceChild(
                        "cube_r2",
                        CubeListBuilder.create()
                                .texOffs(29, 4)
                                .addBox(
                                        19.5F,
                                        -1.0F,
                                        3.5F,
                                        3.5F,
                                        0.25F,
                                        1.0F,
                                        new CubeDeformation(0.0F)),
                        PartPose.offsetAndRotation(18.75F, 0.0F, -1.0F, 0.0F, 0.0F, -1.5708F));

        PartDefinition cube_r3 =
                bone.addOrReplaceChild(
                        "cube_r3",
                        CubeListBuilder.create()
                                .texOffs(0, 82)
                                .addBox(
                                        19.5F,
                                        -1.0F,
                                        3.5F,
                                        3.5F,
                                        0.25F,
                                        1.0F,
                                        new CubeDeformation(0.0F))
                                .texOffs(0, 108)
                                .addBox(
                                        1.5F,
                                        -1.0F,
                                        3.5F,
                                        18.0F,
                                        0.5F,
                                        1.0F,
                                        new CubeDeformation(0.0F)),
                        PartPose.offsetAndRotation(1.0F, 0.0F, -1.0F, 0.0F, 0.0F, -1.5708F));

        PartDefinition cube_r4 =
                bone.addOrReplaceChild(
                        "cube_r4",
                        CubeListBuilder.create()
                                .texOffs(0, 25)
                                .addBox(
                                        1.5F,
                                        -1.0F,
                                        3.5F,
                                        18.0F,
                                        0.5F,
                                        1.0F,
                                        new CubeDeformation(0.0F)),
                        PartPose.offsetAndRotation(16.5F, 0.0F, -1.0F, 0.0F, 0.0F, -1.5708F));

        PartDefinition cube_r5 =
                bone.addOrReplaceChild(
                        "cube_r5",
                        CubeListBuilder.create()
                                .texOffs(30, 0)
                                .addBox(
                                        -8.75F,
                                        -0.3F,
                                        -0.3F,
                                        17.5F,
                                        0.6F,
                                        0.6F,
                                        new CubeDeformation(0.0F)),
                        PartPose.offsetAndRotation(9.0F, -22.5F, 3.0F, -0.7854F, 0.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 128, 128);
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
        bone.render(poseStack, vertexConsumer, packedLight, packedOverlay, net.minecraft.util.FastColor.ARGB32.color((int) (alpha * 255), (int) (red * 255), (int) (green * 255), (int) (blue * 255)));
    }
    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, int color) {
        renderToBuffer(poseStack, consumer, light, overlay,
                ((color >>> 16) & 255) / 255.0f, ((color >>> 8) & 255) / 255.0f,
                (color & 255) / 255.0f, ((color >>> 24) & 255) / 255.0f);
    }
}
