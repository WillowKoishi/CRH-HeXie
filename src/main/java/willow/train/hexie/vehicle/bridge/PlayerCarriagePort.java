package willow.train.hexie.vehicle.bridge;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.PlayerCarriageBridge;
import willow.train.hexie.vehicle.player.RemotePlayerInterpolation;
import willow.train.hexie.vehicle.player.StandaloneHexieSeatEntity;
import willow.train.hexie.vehicle.player.SupportFrame;

final class PlayerCarriagePort implements PlayerCarriageBridge {

    @Override
    public boolean isHexieSeat(Entity entity) {
        return entity instanceof StandaloneHexieSeatEntity || (ModList.get().isLoaded("create") && HexieBridges.createPlayer().seats().isHexieSeat(entity));
    }

    @Override public Entity createHexieSeatEntity(EntityType<?> type, Level level) {
        return ModList.get().isLoaded("create") ? HexieBridges.createPlayer().seats().createHexieSeatEntity(type, level)
                : new StandaloneHexieSeatEntity(type, level);
    }

    @Override public Entity createHexieSeatEntity(Level level) {
        return createHexieSeatEntity(HexieBridges.worldRegistry().entityInit().yzSeatEntity(), level);
    }

    @Override
    public RemotePlayerInterpolationView createRemotePlayerInterpolation(Vec3 position, long sampleTick) {
        return new RemotePlayerInterpolation(position, sampleTick);
    }

    @Override
    public SupportFrameView createSupportFrame(Vec3 origin, Vec3 x, Vec3 y, Vec3 z) {
        return new SupportFrame(origin, x, y, z);
    }
}
