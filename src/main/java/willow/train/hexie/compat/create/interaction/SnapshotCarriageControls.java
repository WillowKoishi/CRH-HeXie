package willow.train.hexie.compat.create.interaction;

import java.util.Collection;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsServerHandler;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsInteractionBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.compat.create.motion.SnapshotCarriageEntity;
import willow.train.hexie.compat.create.track.CreateTrackCompatibility;

// split from SnapshotCarriageEntity.java
public final class SnapshotCarriageControls {
    private final SnapshotCarriageEntity carriageEntity;

    public SnapshotCarriageControls(SnapshotCarriageEntity carriageEntity) { this.carriageEntity = carriageEntity; }

    public boolean handlePlayerInteraction(Player player, BlockPos localPos, Direction side, InteractionHand hand) {
        if (!carriageEntity.behavioursActive) return false;
        if (SnapshotFloorInteraction.handle(carriageEntity, player, localPos, hand)
                || SnapshotCouplerInteraction.handle(carriageEntity, player, localPos, hand)) return true;
        boolean nativeControls = carriageEntity.getContraption().getInteractors().get(localPos) instanceof ControlsInteractionBehaviour
                && !AllItems.WRENCH.isIn(player.getItemInHand(hand));
        if (nativeControls && carriageEntity.level().isClientSide)
            return HexieBridges.Client.render().snapshotControlsClient().click(carriageEntity, localPos, player);

        boolean handled = carriageEntity.baseHandlePlayerInteraction(player, localPos, side, hand);
        if (nativeControls && handled && player instanceof ServerPlayer driver
                && carriageEntity.getControllingPlayer().filter(player.getUUID()::equals).isPresent())
            HexieBridges.sync().sendNativeControlsStartedToPlayer(driver, carriageEntity.getId(), localPos);
        return handled;
    }

    public boolean startControlling(BlockPos controls, Player player) {
        var info = carriageEntity.getContraption() == null ? null : carriageEntity.getContraption().getBlocks().get(controls);
        if (!carriageEntity.behavioursActive || player.isSpectator() || info == null || !(info.state().getBlock() instanceof ControlsBlock)
                || !carriageEntity.canInteractWithBlock(player, controls, 4)) return false;
        return carriageEntity.level().isClientSide || carriageEntity.motion() != null && carriageEntity.motion().startControl(carriageEntity, controls, player);
    }

    public boolean control(BlockPos controls, Collection<Integer> keys, Player player) {
        return carriageEntity.motion() != null && carriageEntity.motion().control(carriageEntity, controls, keys, player);
    }

    /** The driver already left these controls locally. A delayed exit must match the live desk and driver. */
    public void releaseControls(Player player, BlockPos controls) {
        if (carriageEntity.level().isClientSide || player.level() != carriageEntity.level()
                || !player.getUUID().equals(carriageEntity.getControllingPlayer().orElse(null))) return;
        var owner = carriageEntity.motion();
        if (owner == null || !owner.ownsNativeControls(carriageEntity.getUUID(), controls, player.getUUID())) return;
        owner.stopControl(carriageEntity, controls);
        ControlsServerHandler.receivedInputs.get(carriageEntity.level()).remove(player.getUUID());
        CreateTrackCompatibility.clearManualInput(carriageEntity, controls, player.getUUID());
        carriageEntity.setControllingPlayer(null);
    }

    public void stopControlling(BlockPos controls) {
        if (carriageEntity.motion() != null) carriageEntity.motion().stopControl(carriageEntity, controls);
        carriageEntity.baseStopControlling(controls);
    }
}
