package willow.train.hexie.render.carriage;

import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.compat.CreateCarriageBridge.SnapshotBogeyAnimationFrameView;
import willow.train.hexie.bridge.compat.SnapshotCarriageView;
import willow.train.hexie.core.datafix.Constants;
import willow.train.hexie.render.bogey.BogeyRenderData;
import willow.train.hexie.render.bogey.QJRunningGear;

// split from willow/train/hexie/client/render/SnapshotCarriageRenderer.java, lines 91-105
public final class SnapshotBogeyRenderInputs {
    public static CompoundTag data(BogeyRenderData bogey, SnapshotBogeyAnimationFrameView frame) {
        CompoundTag data = bogey.data();
        // The complete model orientation is already in the support transform. Keep renderer-local
        // forward/backward variants, but don't apply Create's assembly-direction correction twice.
        NBTHelper.writeEnum(data, Constants.BOGEY_ASSEMBLY_DIRECTION_KEY, Direction.NORTH);
        data.putFloat(QJRunningGear.QJBogeyRenderer.TRAIN_SPEED_KEY, frame.speed());
        return data;
    }

    public static int light(SnapshotCarriageView entity, SnapshotBogeyAnimationFrameView frame, float partialTick) {
        Vec3 world = entity.asEntity().toGlobalVector(new Vec3(frame.x(), frame.y(), frame.z()), partialTick);
        BlockPos pos = BlockPos.containing(world);
        return LightTexture.pack(entity.asEntity().level().getBrightness(LightLayer.BLOCK, pos),
                entity.asEntity().level().getBrightness(LightLayer.SKY, pos));
    }
}
