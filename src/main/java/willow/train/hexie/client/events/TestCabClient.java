package willow.train.hexie.client.events;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import willow.train.hexie.blocks.emu.TestCabInteraction;
import willow.train.hexie.blocks.emu.TestDrivingConsoleBlock;
import willow.train.hexie.network.HexieNetworkHandler;
import willow.train.hexie.network.c2s.TestCabActionPacket;

@EventBusSubscriber(modid = "hexie", value = Dist.CLIENT)
public final class TestCabClient {
    @SubscribeEvent public static void click(DrivingConsoleClickEvent event) {
        var state = event.context.seatAccess().blockState(event.context.console());
        if (!(state.getBlock() instanceof TestDrivingConsoleBlock)) return;
        int action = TestCabInteraction.action(state, event.origin, event.direction);
        if (action >= 0) HexieNetworkHandler.sendToServer(new TestCabActionPacket(event.context.seatAccess().vehicle().getId(), action));
    }
}
