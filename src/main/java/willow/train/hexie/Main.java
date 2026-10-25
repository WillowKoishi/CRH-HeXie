package willow.train.hexie;

import com.simibubi.create.foundation.data.CreateRegistrate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import willow.train.hexie.compat.HeXieSimurailCompat;
import willow.train.hexie.creative.*;
import willow.train.hexie.init.*;
import willow.train.hexie.init.block.BlockInit;
import willow.train.hexie.init.bogeys.BogeyEntityInit;
import willow.train.hexie.init.bogeys.BogeySizesInit;
import willow.train.hexie.init.bogeys.BogeyStylesInit;
import willow.train.hexie.init.client.HeXieClientInit;
import willow.train.hexie.init.client.MenuInit;
import willow.train.hexie.init.item.AllItemsInit;
import willow.train.hexie.init.item.LogoItemInit;
import willow.train.hexie.init.recipe.RecipeInit;
import willow.train.hexie.init.recipe.RecipeSerializerInit;
import willow.train.hexie.init.semaphore.SemaphoreBlockInit;
import willow.train.hexie.init.semaphore.SemaphoreEntityInit;
import willow.train.hexie.init.tags.TagsInit;
import willow.train.hexie.init.track.TrackBlockInit;
import willow.train.hexie.init.track.TrackItems;
import willow.train.hexie.init.track.TrackMaterialInit;
import willow.train.hexie.interaction.InteractionProcessor;
import willow.train.hexie.network.Env;
import willow.train.hexie.network.HexieNetworkHandler;
import willow.train.hexie.util.enums.PanelTypesUtil;

import java.util.Random;

@Mod("hexie")
public class Main {
    public static final Logger LOGGER = LoggerFactory.getLogger("HeXie");

    public static final String MOD_ID = "hexie";
    public static final boolean INTERNAL_TEST = false;
    public static final Random HEXIE_RANDOM = new Random();
    public static IEventBus bus;
    private static ModContainer modContainer;

    private static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MOD_ID);

    static {
        REGISTRATE.defaultCreativeTab((ResourceKey<CreativeModeTab>) null);
    }

    public static PanelTypesUtil PANEL_TYPE_UTIL;
    public static final MainTab HEXIE_MAIN =
            new MainTab(MOD_ID) {
                @Override
                public @NotNull ItemStack makeIcon() {
                    return super.makeIcon();
                }
            };
    public static final LocosTab HEXIE_LOCOS =
            new LocosTab(MOD_ID) {
                @Override
                public @NotNull ItemStack makeIcon() {
                    return new ItemStack(LogoItemInit.LOCO_LOGOS.get());
                }
            };
    public static final NormalSpeedPassageCarriageTab HEXIE_NORMAL_SPEED_PASSAGE_CARRIAGE_TAB =
            new NormalSpeedPassageCarriageTab(MOD_ID) {
                @Override
                public @NotNull ItemStack makeIcon() {
                    return new ItemStack(LogoItemInit.SERIES_25_LOGOS.get());
                }
            };
    public static final DietTab HEXIE_DIET =
            new DietTab(MOD_ID) {
                @Override
                public @NotNull ItemStack makeIcon() {
                    return new ItemStack(LogoItemInit.CA_25T.get());
                }
            };

    public Main(IEventBus modBus, ModContainer container) {
        bus = modBus;
        modContainer = container;
        bus.addListener(HexieNetworkHandler::registerPackets);
        CreativeTabsInit.register(bus);

        //InternalTestUtil.InternalTest();

        AllItemsInit.registerAllItems(bus);

        BlockInit.registerAllBlocks(bus);

        BlockEntitiesInit.BLOCK_ENTITIES.register(bus);

        EntityInit.ENTITY_TYPES.register(bus);

        modSetup();

        SoundsInit.register(bus);

        EffectInit.register(bus);

        RecipeInit.register(bus);

        RecipeSerializerInit.register(bus);

        REGISTRATE.registerEventListeners(bus);

        bus.addListener(this::setup);

        Env.CLIENT.runIfCurrent(() -> HeXieClientInit::init);

        MenuInit.register(bus);

        PANEL_TYPE_UTIL = new PanelTypesUtil();
    }

    protected void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(InteractionBehaviourInit::register);
        if (ModList.get().isLoaded("simurail")) {
            event.enqueueWork(HeXieSimurailCompat::register);
        }
    }

    public static CreateRegistrate registrate() {
        return REGISTRATE;
    }

    public static ResourceLocation asResource(String name) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, name);
    }

    public static void modSetup() {
        TrackMaterialInit.register();
        BogeyStylesInit.register();
        TrackItems.register();
        BogeySizesInit.register();
        TrackBlockInit.register();
        BogeyEntityInit.register();
        TagsInit.register();
        InteractionProcessor.invoke();
        SemaphoreBlockInit.register();
        SemaphoreEntityInit.register();
        //HXConfig.register(modContainer);
    }
}
