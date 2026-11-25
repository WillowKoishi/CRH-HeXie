package willow.train.hexie.ui.screens.sign;

import net.minecraft.network.chat.Component;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.BlockEntityViews.CarriageNoSignEntityView;
import willow.train.hexie.bridge.world.BlockEntityViews.CarriageTypeSignEntityView;
import willow.train.hexie.bridge.world.BlockEntityViews.LacqueredBoardEntityView;
import willow.train.hexie.bridge.world.BlockEntityViews.TrainSpeedSignEntityView;
import willow.train.hexie.ui.menu.BlockEntityEditMenu;

/** Moving editors send their detached draft through the existing packets, without touching a client world cell. */
public final class LegacySignScreenSubmission {
    public static void submit(BlockEntityEditMenu<?> menu) {
        var entity = menu.editTarget();
        if (entity instanceof CarriageNoSignEntityView sign) {
            if (!menu.contentOnly()) { sign.markUpdated(true); return; }
            HexieBridges.sync().sendCarriageNoSignUpdate(menu.containerId, sign.asBlockEntity().getBlockPos(), sign.getMessage(), sign.getColor(), sign.isLeftSide());
        } else if (entity instanceof CarriageTypeSignEntityView sign) {
            if (!menu.contentOnly()) { sign.markUpdated(true); return; }
            HexieBridges.sync().sendCarriageTypeSignUpdate(menu.containerId, sign.asBlockEntity().getBlockPos(),
                    sign.getMessage(0, false).getString(), sign.getMessage(1, false).getString(),
                    sign.getMessage(2, false).getString(), sign.getMessage(3, false).getString(), sign.getMessage(4, false).getString(), sign.getColor());
        } else if (entity instanceof LacqueredBoardEntityView sign) {
            if (!menu.contentOnly()) { sign.markUpdated(true); return; }
            var messages = new Component[16];
            for (int i = 0; i < messages.length; i++) messages[i] = sign.getMessage(i);
            HexieBridges.sync().sendLacqueredBoard(menu.containerId, sign.asBlockEntity().getBlockPos(), messages, sign.getXOffset(),
                    sign.getBackGroundColor(), sign.getForGroundColor(), sign.getBeltForGroundColor());
        } else if (entity instanceof TrainSpeedSignEntityView sign) {
            if (!menu.contentOnly()) { sign.markUpdated(true); return; }
            HexieBridges.sync().sendTrainSpeedSignUpdate(menu.containerId, sign.asBlockEntity().getBlockPos(), sign.getContent(), sign.getColor(),
                    sign.getX_offset(), sign.getY_offset(), sign.isX_revert(), sign.isY_revert());
        }
    }
    private LegacySignScreenSubmission() {}
}
