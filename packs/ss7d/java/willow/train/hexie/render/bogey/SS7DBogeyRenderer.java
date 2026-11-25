package willow.train.hexie.render.bogey;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import net.minecraft.nbt.CompoundTag;
import static willow.train.hexie.render.init.PartialModelInit.*;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.render.BogeyRenderBridge.SS7DBogeyRendererView;

/** The block model owns the fixed body top; only the running frame, wheels and rods animate here. */
public final class SS7DBogeyRenderer extends SimpleBogeyRenderer implements SS7DBogeyRendererView {
    // Source axle centre is Y=15px, tread radius=9.5px; use the existing rail calibration.
    public static final double AXLE_HEIGHT = .3426 + 9.5 / 16;
    public static final double FRAME_OFFSET = AXLE_HEIGHT - 15.0 / 16;
    private static final double AXLE_HALF_SPACING = 23.05 / 16;

    @Override public BogeySizes.BogeySize getSize() { return HexieBridges.createTrain().bogeySizesInit().ss7dFront(); }

    @Override public void render(CompoundTag data, float wheelAngle, PoseStack pose, int light,
                                 VertexConsumer buffer, boolean inContraption) {
        boolean instanced = buffer == null;
        getTransform(SS7D_FRONT_FRAME, pose, instanced).translate(0, FRAME_OFFSET, 0).render(pose, light, buffer);
        var body = BogeySuspensionPose.inModelSpace(data, FRAME_OFFSET);
        getTransform(SS7D_FRONT_TRACTION_LEFT, pose, instanced).translate(0, FRAME_OFFSET, 0)
                .transform(SS7DTractionLink.transform(body, true)).render(pose, light, buffer);
        getTransform(SS7D_FRONT_TRACTION_RIGHT, pose, instanced).translate(0, FRAME_OFFSET, 0)
                .transform(SS7DTractionLink.transform(body, false)).render(pose, light, buffer);
        getTransform(SS7D_FRONT_WHEEL_FRONT, pose, instanced).translate(0, AXLE_HEIGHT, -AXLE_HALF_SPACING)
                .rotateX(wheelAngle).render(pose, light, buffer);
        getTransform(SS7D_FRONT_WHEEL_REAR, pose, instanced).translate(0, AXLE_HEIGHT, AXLE_HALF_SPACING)
                .rotateX(wheelAngle).render(pose, light, buffer);
    }
}
