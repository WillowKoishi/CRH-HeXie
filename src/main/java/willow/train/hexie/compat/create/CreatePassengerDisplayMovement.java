package willow.train.hexie.compat.create;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import willow.train.hexie.api.display.PassengerDisplayHost;
import willow.train.hexie.api.display.PassengerDisplayState;
import willow.train.hexie.client.sign.PassengerDisplayCache;

/** Bind render BEs to the carriage cache, including after Create rebuilds its client contraption. */
public final class CreatePassengerDisplayMovement implements MovementBehaviour {
    private record Binding(BlockEntity blockEntity, CarriageContraptionEntity carriage, Level level) {}

    @Override public void startMoving(MovementContext context) { bind(context); }
    @Override public void tick(MovementContext context) { bind(context); }
    @Override public boolean mustTickWhileDisabled() { return true; }
    @Override public boolean disableBlockEntityRendering() { return false; }

    private static void bind(MovementContext context) {
        if (!context.world.isClientSide
                || !(context.contraption.entity instanceof CarriageContraptionEntity carriage)) return;
        var blockEntity = context.contraption.getBlockEntityClientSide(context.localPos);
        if (!(blockEntity instanceof PassengerDisplayHost host)) return;
        if (context.temporaryData instanceof Binding previous
                && previous.blockEntity() == blockEntity && previous.carriage() == carriage
                && previous.level() == carriage.level()) return;
        var level = carriage.level();
        var id = carriage.getUUID();
        host.passengerDisplaySource(() -> PassengerDisplayCache.read(level, id));
        context.temporaryData = new Binding(blockEntity, carriage, level);
    }

    @Override public void stopMoving(MovementContext context) {
        if (context.world.isClientSide && context.temporaryData instanceof Binding binding
                && binding.blockEntity() instanceof PassengerDisplayHost host)
            host.passengerDisplaySource(() -> PassengerDisplayState.EMPTY);
        context.temporaryData = null;
    }
}
