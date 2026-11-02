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
    private static boolean approachHeld;
    private static willow.train.hexie.blocks.locos.DrivingConsoleContext previous;

    private static boolean isTest(willow.train.hexie.blocks.locos.DrivingConsoleContext context) {
        return context != null && context.seatAccess().blockState(context.console()).getBlock() instanceof TestDrivingConsoleBlock;
    }
    private static void send(willow.train.hexie.blocks.locos.DrivingConsoleContext context, int action) {
        if (isTest(context)) HexieNetworkHandler.sendToServer(new TestCabActionPacket(context.seatAccess().vehicle().getId(), action));
    }
    @SubscribeEvent public static void key(DrivingConsoleKeyEvent event) {
        if (!isTest(event.context) || !net.minecraft.client.Minecraft.getInstance().options.keyJump.matches(event.key, event.scanCode)) return;
        approachHeld = event.action != org.lwjgl.glfw.GLFW.GLFW_RELEASE;
        previous = event.context;
        if (event.action != org.lwjgl.glfw.GLFW.GLFW_REPEAT) send(event.context, approachHeld ? 7 : 8);
        event.consume();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var context = DrivingConsoleClient.context();
        if (!java.util.Objects.equals(previous, context) || !DrivingConsoleClient.capturesInput()) {
            if (approachHeld) send(previous, 8);
            approachHeld = false;
        }
        previous = context;
        if (isTest(context) && ++ticks % 10 == 0) send(context, approachHeld ? 7 : -1);
    }
    @SubscribeEvent public static void click(DrivingConsoleClickEvent event) {
        var state = event.context.seatAccess().blockState(event.context.console());
        if (!(state.getBlock() instanceof TestDrivingConsoleBlock)) return;
        int action = willow.train.hexie.blocks.locos.TestCabInteraction.action(state, event.origin, event.direction);
        if (action >= 0) HexieNetworkHandler.sendToServer(new TestCabActionPacket(event.context.seatAccess().vehicle().getId(), action));
    }
}
