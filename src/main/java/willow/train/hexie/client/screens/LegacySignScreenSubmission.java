package willow.train.hexie.client.screens;

import net.minecraft.network.chat.Component;
import willow.train.hexie.blockentity.CarriageNoSignEntity;
import willow.train.hexie.blockentity.CarriageTypeSignEntity;
import willow.train.hexie.blockentity.LacqueredBoardEntity;
import willow.train.hexie.blockentity.TrainSpeedSignEntity;
import willow.train.hexie.menu.BlockEntityEditMenu;
import willow.train.hexie.network.HexieNetworkHandler;
import willow.train.hexie.network.c2s.signs.*;

/** Moving editors send their detached draft through the existing packets, without touching a client world cell. */
final class LegacySignScreenSubmission {
    static void submit(BlockEntityEditMenu<?> menu) {
        var entity = menu.editTarget();
        if (entity instanceof CarriageNoSignEntity sign) {
            if (!menu.contentOnly()) { sign.markUpdated(true); return; }
            HexieNetworkHandler.sendToServer(new CarriageNoSignUpdatePacket(sign.getBlockPos(), sign.getMessage(), sign.getColor(), sign.isLeftSide()).withMenuId(menu.containerId));
        } else if (entity instanceof CarriageTypeSignEntity sign) {
            if (!menu.contentOnly()) { sign.markUpdated(true); return; }
            HexieNetworkHandler.sendToServer(new CarriageTypeSignUpdatePacket(sign.getBlockPos(),
                    sign.getMessage(0, false).getString(), sign.getMessage(1, false).getString(),
                    sign.getMessage(2, false).getString(), sign.getMessage(3, false).getString(), sign.getMessage(4, false).getString(), sign.getColor()).withMenuId(menu.containerId));
        } else if (entity instanceof LacqueredBoardEntity sign) {
            if (!menu.contentOnly()) { sign.markUpdated(true); return; }
            var messages = new Component[16];
            for (int i = 0; i < messages.length; i++) messages[i] = sign.getMessage(i);
            HexieNetworkHandler.sendToServer(new LacqueredBoardPacket(sign.getBlockPos(), messages, sign.getXOffset(),
                    sign.getBackGroundColor(), sign.getForGroundColor(), sign.getBeltForGroundColor()).withMenuId(menu.containerId));
        } else if (entity instanceof TrainSpeedSignEntity sign) {
            if (!menu.contentOnly()) { sign.markUpdated(true); return; }
            HexieNetworkHandler.sendToServer(new TrainSpeedSignUpdatePacket(sign.getBlockPos(), sign.getContent(), sign.getColor(),
                    sign.getX_offset(), sign.getY_offset(), sign.isX_revert(), sign.isY_revert()).withMenuId(menu.containerId));
        }
    }
    private LegacySignScreenSubmission() {}
}
