package willow.train.hexie.mixins.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import willow.train.hexie.vehicle.player.CarriedPlayerConnection;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinCarriedPlayerConnection implements CarriedPlayerConnection {
    @Shadow private double firstGoodX;
    @Shadow private double firstGoodY;
    @Shadow private double firstGoodZ;
    @Shadow private double lastGoodX;
    @Shadow private double lastGoodY;
    @Shadow private double lastGoodZ;
    @Shadow private Vec3 awaitingPositionFromClient;
    @Shadow private int aboveGroundTickCount;
    @Shadow private boolean clientIsFloating;

    @Override public void hexie$grounded() { aboveGroundTickCount = 0; clientIsFloating = false; }

    @Override public boolean hexie$canCarry() { return awaitingPositionFromClient == null; }

    @Override public void hexie$carry(Vec3 displacement) {
        firstGoodX += displacement.x; firstGoodY += displacement.y; firstGoodZ += displacement.z;
        lastGoodX += displacement.x; lastGoodY += displacement.y; lastGoodZ += displacement.z;
    }
}
