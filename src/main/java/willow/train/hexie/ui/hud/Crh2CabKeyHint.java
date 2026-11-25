package willow.train.hexie.ui.hud;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2DrivingConsoleBlockView;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.core.api.cab.DrivingConsoleContext;

/** Shows the desk's held-key controls once on entering detailed driving mode. */
@EventBusSubscriber(modid = HexieConstants.MOD_ID, value = Dist.CLIENT)
public final class Crh2CabKeyHint {
    private record SeatIdentity(UUID vehicle, BlockPos seat, BlockPos console) {}

    private static SeatIdentity active;
    private static boolean detailed;
    private static boolean shown;

    private Crh2CabKeyHint() {}

    @SubscribeEvent
    public static void afterClientTick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        var cab = minecraft.level == null || minecraft.player == null || minecraft.player.isSpectator()
                ? null : DrivingConsoleContext.find(minecraft.player);
        var state = cab == null ? null : cab.seatAccess().blockState(cab.console());
        if (state == null || !(state.getBlock() instanceof CRH2DrivingConsoleBlockView desk)
                || desk.trainControl(state) != HexieBridges.cabControl().crh2CabControls().instance()) {
            active = null;
            detailed = false;
            shown = false;
            return;
        }

        var identity = new SeatIdentity(cab.seatAccess().vehicle().getUUID(), cab.seat().immutable(),
                cab.console().immutable());
        boolean nextDetailed = !cab.simple();
        if (!identity.equals(active) || detailed != nextDetailed) {
            active = identity;
            detailed = nextDetailed;
            shown = false;
        }
        if (!detailed || shown || minecraft.screen != null || minecraft.getOverlay() != null
                || minecraft.options.hideGui) return;

        // Vanilla keeps this action-bar message for 60 ticks, including its fade-out.
        minecraft.gui.setOverlayMessage(desk.keyboardHint(state), false);
        shown = true;
    }
}
