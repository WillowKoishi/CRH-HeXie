package willow.train.hexie.event.client;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import willow.train.hexie.Main;
import willow.train.hexie.client.render.bogey.DF11GBogeyRenderer;
import willow.train.hexie.datafix.Constants;
import willow.train.hexie.init.bogeys.BogeySizesInit;
import willow.train.hexie.init.bogeys.BogeyStylesInit;

import java.util.HashSet;
import java.util.Set;


@EventBusSubscriber(modid = Main.MOD_ID, value = Dist.CLIENT)
public final class TrainBogeyClientSync {

    private static final Set<CarriageContraptionEntity> LOADED_CARRIAGES = new HashSet<>();
    private static ClientLevel trackedLevel;

    private TrainBogeyClientSync() {}

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ClientLevel level)
                || !(event.getEntity() instanceof CarriageContraptionEntity entity))
            return;
        if (trackedLevel != level) {
            LOADED_CARRIAGES.clear();
            trackedLevel = level;
        }
        LOADED_CARRIAGES.add(entity);
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof CarriageContraptionEntity entity)
            LOADED_CARRIAGES.remove(entity);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level != trackedLevel) {
            LOADED_CARRIAGES.clear();
            trackedLevel = minecraft.level;
            return;
        }

        for (CarriageContraptionEntity entity : LOADED_CARRIAGES) {
            Carriage carriage = entity.getCarriage();
            if (carriage != null)
                updateCarriage(carriage, entity);
        }
    }

    private static void updateCarriage(Carriage carriage, CarriageContraptionEntity entity) {
        CarriageBogey firstBogey = carriage.bogeys.getFirst();
        CarriageBogey secondBogey = carriage.bogeys.getSecond();
        boolean updateFirst = isHexieBogey(firstBogey);
        boolean updateSecond = isHexieBogey(secondBogey);
        if (!updateFirst && !updateSecond)
            return;

        Direction assemblyDirection = Direction.fromYRot(entity.getInitialOrientation().toYRot() + 90);

        float signedSpeed = 0;
        boolean firstNeedsSpeed = updateFirst && BogeyStylesInit.DF11GBOGEY.equals(firstBogey.getStyle())
                && isQJSize(firstBogey);
        boolean secondNeedsSpeed = updateSecond && BogeyStylesInit.DF11GBOGEY.equals(secondBogey.getStyle())
                && isQJSize(secondBogey);
        if (firstNeedsSpeed || secondNeedsSpeed) {
            Vec3 difference = entity.getPrevPositionVec()
                    .subtract(entity.getX(), entity.getY(), entity.getZ());
            Vec3 relativeDifference = VecHelper.rotate(difference, entity.yaw, Direction.Axis.Y);
            double sign = Math.signum(relativeDifference.x);
            signedSpeed = (float) (Math.abs(difference.length()) * sign);
        }

        if (updateFirst)
            updateBogey(firstBogey, assemblyDirection, signedSpeed, firstNeedsSpeed);
        if (updateSecond)
            updateBogey(secondBogey, assemblyDirection, signedSpeed, secondNeedsSpeed);
    }

    private static boolean isQJSize(CarriageBogey bogey) {
        return BogeySizesInit.QJ.equals(bogey.getSize())
                || BogeySizesInit.QJ_BACKWARD.equals(bogey.getSize());
    }

    private static boolean isHexieBogey(CarriageBogey bogey) {
        // Create can expose a carriage while one of its bogey ends is still being assembled.
        if (bogey == null || bogey.bogeyData == null)
            return false;
        var style = bogey.getStyle();
        return BogeyStylesInit.HXSTANDARDBOGEY.equals(style) || BogeyStylesInit.DF11GBOGEY.equals(style);
    }

    private static void updateBogey(CarriageBogey bogey, Direction direction, float trainSpeed,
                                   boolean needsSpeed) {
        CompoundTag bogeyData = bogey.bogeyData;
        if (direction != null
                && !direction.name().equals(bogeyData.getString(Constants.BOGEY_ASSEMBLY_DIRECTION_KEY)))
            NBTHelper.writeEnum(bogeyData, Constants.BOGEY_ASSEMBLY_DIRECTION_KEY, direction);

        if (needsSpeed) {
            String speedKey = DF11GBogeyRenderer.QJBogeyRenderer.TRAIN_SPEED_KEY;
            if (Float.compare(bogeyData.getFloat(speedKey), trainSpeed) != 0)
                bogeyData.putFloat(speedKey, trainSpeed);
        }
    }
}
