package willow.train.hexietrain;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 物理列车附属模组 —— 入口。
 *
 * <p>离线构建方案：编译期 classpath 直接指向本机整合包
 * （NeoForge 21.1.248 + MC 1.21.1 补丁 jar + Create 6.0.10 + Sable 2.0.5 + Simurail），
 * 不需要 maven.neoforged.net。细节见 BUILD-NOTES.md。
 */
@Mod(HexieTrain.MOD_ID)
public class HexieTrain {

    public static final String MOD_ID = "hexietrain";
    public static final Logger LOGGER = LoggerFactory.getLogger("HexieTrain");

    public HexieTrain(IEventBus modBus, ModContainer container) {
        HexieRegistries.register(modBus);
        SubLevelSuspend.init();
        LOGGER.info("[HexieTrain] 载入：物理列车附属模组");
    }

    public static net.minecraft.resources.ResourceLocation id(String path) {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}