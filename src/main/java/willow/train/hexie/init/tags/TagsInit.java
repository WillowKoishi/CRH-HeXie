package willow.train.hexie.init.tags;

import static willow.train.hexie.init.tags.TagsInit.NameSpace.MOD;

import net.createmod.catnip.lang.Lang;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import willow.train.hexie.Main;

public class TagsInit {

    public enum NameSpace {
        MOD(Main.MOD_ID, false, true),
        FORGE("c");

        public final String id;
        public final boolean optionalDefault;
        public final boolean alwaysDatagenDefault;

        NameSpace(String id) {
            this(id, true, false);
        }

        NameSpace(String id, boolean optionalDefault, boolean alwaysDatagenDefault) {
            this.id = id;
            this.optionalDefault = optionalDefault;
            this.alwaysDatagenDefault = alwaysDatagenDefault;
        }
    }

    public enum AllBlockTags {
        SEMAPHORE_POLES(MOD, MOD.optionalDefault, false),
        TRACK_CASING_BLACKLIST(MOD, MOD.optionalDefault, false),
        CONDUCTOR_SPY_USABLE(
                MOD,
                MOD.optionalDefault,
                false) // so other mods / datapacks can make more blocks usable for conductor spies
    ;

        public final TagKey<Block> tag;

        AllBlockTags() {
            this(MOD);
        }

        AllBlockTags(TagsInit.NameSpace namespace) {
            this(namespace, namespace.optionalDefault, namespace.alwaysDatagenDefault);
        }

        AllBlockTags(TagsInit.NameSpace namespace, String path) {
            this(namespace, path, namespace.optionalDefault, namespace.alwaysDatagenDefault);
        }

        AllBlockTags(TagsInit.NameSpace namespace, boolean optional, boolean alwaysDatagen) {
            this(namespace, null, optional, alwaysDatagen);
        }

        AllBlockTags(
                TagsInit.NameSpace namespace,
                String path,
                boolean optional,
                boolean alwaysDatagen) {
            ResourceLocation id =
                    ResourceLocation.fromNamespaceAndPath(namespace.id, path == null ? Lang.asId(name()) : path);
            tag = optionalTag(net.minecraft.core.registries.Registries.BLOCK, id);
        }

        @SuppressWarnings("deprecation")
        public boolean matches(Block block) {
            return block.builtInRegistryHolder().is(tag);
        }

        public static void register() {}
    }

    public enum AllItemTags {
        CONDUCTOR_CAPS;

        public final TagKey<Item> tag;
        public final boolean alwaysDatagen;

        AllItemTags() {
            this(MOD);
        }

        AllItemTags(TagsInit.NameSpace namespace) {
            this(namespace, namespace.optionalDefault, namespace.alwaysDatagenDefault);
        }

        AllItemTags(TagsInit.NameSpace namespace, String path) {
            this(namespace, path, namespace.optionalDefault, namespace.alwaysDatagenDefault);
        }

        AllItemTags(TagsInit.NameSpace namespace, boolean optional, boolean alwaysDatagen) {
            this(namespace, null, optional, alwaysDatagen);
        }

        AllItemTags(
                TagsInit.NameSpace namespace,
                String path,
                boolean optional,
                boolean alwaysDatagen) {
            ResourceLocation id =
                    ResourceLocation.fromNamespaceAndPath(namespace.id, path == null ? Lang.asId(name()) : path);
            tag = optionalTag(net.minecraft.core.registries.Registries.ITEM, id);
            this.alwaysDatagen = alwaysDatagen;
        }

        public static void register() {}
    }

    public static <T> TagKey<T> optionalTag(net.minecraft.resources.ResourceKey<? extends Registry<T>> registry, ResourceLocation id) {
        return TagKey.create(registry, id);
    }

    // load all classes
    public static void register() {
        TagsInit.AllBlockTags.register();
        TagsInit.AllItemTags.register();
    }
}
