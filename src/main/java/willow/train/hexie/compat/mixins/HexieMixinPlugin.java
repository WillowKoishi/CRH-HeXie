package willow.train.hexie.compat.mixins;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Selects integrations from discovery metadata before Mixin resolves their targets. */
public final class HexieMixinPlugin implements IMixinConfigPlugin {
    private static final String PACKAGE = HexieMixinPlugin.class.getPackageName() + ".";

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String name = mixinClassName.substring(PACKAGE.length());
        if (name.startsWith("client.")) name = name.substring("client.".length());

        if (name.startsWith("cee.paw.")) {
            return loaded("paw") || getClass().getClassLoader().getResource("de/mrjulsen/paw/block/PantographBlock.class") != null;
        }
        if (name.startsWith("cee.")) return loaded("electroenergetics");
        if (name.startsWith("sable.")) return loaded("sable");
        if (name.equals("simurail.MixinSimulatedPhysicalHeadAssembly")) return loaded("simulated");
        if (name.startsWith("simurail.")) return loaded("simurail");
        if (name.equals("track.MixinRailxTrackValueBox") || name.startsWith("render.MixinRailx")) return loaded("railx");
        if (name.equals("render.MixinSodiumChunkVertexConsumer")) return loaded("sodium");

        if (name.startsWith("create.") || name.startsWith("bogey.") || name.startsWith("carriage.")
                || name.startsWith("signal.") || name.startsWith("player.")
                || name.startsWith("render.MixinCurve") || name.startsWith("render.MixinBallast")
                || name.startsWith("render.MixinCarriage") || name.startsWith("input.MixinDrivingConsole")) {
            return loaded("create");
        }
        return switch (name) {
            case "track.MixinTrackBallastData", "render.MixinClientContraptionDoor",
                    "render.MixinCRH2DoorRenderContext",
                    "input.MixinCarriageInteraction",
                    "collision.MixinCarriagePlayerCollision", "collision.MixinCarriageCollisionChunks",
                    "ui.MixinSchematicEditScreen", "ui.MixinSchematicPromptScreen" -> loaded("create");
            default -> true;
        };
    }

    private static boolean loaded(String modId) {
        return FMLLoader.getLoadingModList().getModFileById(modId) != null;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
