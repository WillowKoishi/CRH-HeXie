package willow.train.hexie.mixins.mixin.client;

import com.crystaelix.simurail.content.bogey.PhysicsBogeyOptions;
import org.joml.Matrix4f;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import willow.train.hexie.client.render.bogey.SimurailBogeySuspensionPose;

@Pseudo
@Mixin(targets = "com.crystaelix.simurail.content.bogey.PhysicsBogeyBlockEntity", remap = false)
public abstract class MixinPhysicsBogeySuspensionData implements SimurailBogeySuspensionPose.Access {
    @Shadow protected PhysicsBogeyOptions options;
    @Shadow public abstract Vector3f getRenderPivotOffset(float partialTick, Vector3f destination);
    @Shadow public abstract Quaternionf getRenderPivotRot(float partialTick, Quaternionf destination);
    @Shadow public abstract Quaterniondc getJointOrientation();
    @Shadow public abstract boolean isInverted();

    @Override
    public Matrix4f hexie$suspensionPose(float partialTick) {
        return SimurailBogeySuspensionPose.relative(getRenderPivotOffset(partialTick, new Vector3f()),
                getRenderPivotRot(partialTick, new Quaternionf()), new Quaternionf(getJointOrientation()),
                (isInverted() ? 1 : -1) * options.getAxleOffset());
    }
}
