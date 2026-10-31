package willow.train.hexie.client.events;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import willow.train.hexie.blocks.locos.CRH2DrivingConsoleBlock;
import willow.train.hexie.blocks.locos.TestDrivingConsoleBlock;
import willow.train.hexie.network.HexieNetworkHandler;
import willow.train.hexie.network.c2s.TestCabActionPacket;

@EventBusSubscriber(modid = "hexie", value = Dist.CLIENT)
public final class TestCabClient {
    private static int ticks;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var context = DrivingConsoleClient.context();
        if (context != null && context.seatAccess().blockState(context.console()).getBlock() instanceof TestDrivingConsoleBlock
                && ++ticks % 10 == 0)
            HexieNetworkHandler.sendToServer(new TestCabActionPacket(context.seatAccess().vehicle().getId(), -1));
    }
    @SubscribeEvent public static void click(DrivingConsoleClickEvent event) {
        var state = event.context.seatAccess().blockState(event.context.console());
        if (!(state.getBlock() instanceof TestDrivingConsoleBlock)) return;
        int action = willow.train.hexie.blocks.locos.TestCabInteraction.action(state, event.origin, event.direction);
        if (action >= 0) HexieNetworkHandler.sendToServer(new TestCabActionPacket(event.context.seatAccess().vehicle().getId(), action));
    }
}
