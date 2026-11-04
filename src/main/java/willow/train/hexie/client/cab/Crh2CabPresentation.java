package willow.train.hexie.client.cab;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.blockentity.DrivingConsoleEntity;
import willow.train.hexie.blocks.emu.CRH2CabGeometry;
import willow.train.hexie.blocks.emu.CRH2DrivingConsoleBlock;
import willow.train.hexie.blocks.emu.DrivingConsoleContext;
import willow.train.hexie.vehicle.control.CabModule;
import willow.train.hexie.vehicle.control.Crh2CabHandle;

/** Share the world's animation clock between visual and pointer sampling, including virtual cabs. */
public final class Crh2CabPresentation {
    private Crh2CabPresentation() {}

    public static double brake(DrivingConsoleEntity cab, CabModule.State controls) {
        var mc = Minecraft.getInstance();
        int notch = Crh2CabHandle.BRAKE.notch(controls);
        if (mc.level == null) return notch;
        double time = mc.level.getGameTime() + (double) mc.getTimer().getGameTimeDeltaPartialTick(false);
        return cab.visualBrakePosition(notch, time);
    }

    public static Crh2CabHandle hit(DrivingConsoleContext context, Vec3 origin, Vec3 direction) {
        var seat = context.seatAccess();
        var state = seat.blockState(context.console());
        var controls = seat.cabControls(context.console());
        var vehicle = seat.vehicle();
        var blockEntity = vehicle instanceof AbstractContraptionEntity car && car.getContraption() != null
                ? car.getContraption().getBlockEntityClientSide(context.console())
                : vehicle.level().getBlockEntity(context.console());
        double position = blockEntity instanceof DrivingConsoleEntity cab ? brake(cab, controls)
                : Crh2CabHandle.BRAKE.notch(controls);
        return CRH2CabGeometry.hit(state.getValue(CRH2DrivingConsoleBlock.FACING), origin, direction, controls, position);
    }
}
