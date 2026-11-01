package willow.train.hexietrain.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import willow.train.hexietrain.HexieRegistries;
import willow.train.hexietrain.HexieTrain;
import willow.train.hexietrain.TrainDispatcherMenu;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 客户端注册：把菜单和界面绑定起来。
 *
 * <p>这里用反射调用 {@code RegisterMenuScreensEvent.register}，
 * 原因和方块实体类型一样：对着运行时 jar 编译时，
 * {@code MenuScreens.ScreenConstructor} 是包内可见的接口，编译期拿不到。
 * 运行时它可用（所有模组都这么注册）。
 */
@EventBusSubscriber(modid = HexieTrain.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HexieClientSetup {

    private HexieClientSetup() {}

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public static void registerScreens(RegisterMenuScreensEvent event) {
        try {
            Class<?> constructorType = Class.forName(
                    "net.minecraft.client.gui.screens.MenuScreens$ScreenConstructor");

            Object factory = Proxy.newProxyInstance(
                    constructorType.getClassLoader(),
                    new Class<?>[]{constructorType},
                    (proxy, method, args) -> {
                        if (args == null || args.length < 3) return null;
                        return new TrainDispatcherScreen(
                                (TrainDispatcherMenu) args[0],
                                (Inventory) args[1],
                                (Component) args[2]);
                    });

            Method register = RegisterMenuScreensEvent.class
                    .getMethod("register", MenuType.class, constructorType);
            register.invoke(event, HexieRegistries.TRAIN_DISPATCHER_MENU.get(), factory);

        } catch (Throwable t) {
            HexieTrain.LOGGER.error("[HexieTrain] 注册调度器界面失败", t);
        }
    }
}