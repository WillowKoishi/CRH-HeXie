package willow.train.hexie.compat.create.railx;

import com.lhwdev.minecraft.railx.flexiTrack.FlexiTrackMaterial;
import com.lhwdev.minecraft.railx.registry.AllBlockEntityTypes;
import com.simibubi.create.AllTags;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.tterrag.registrate.providers.ProviderType;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import willow.train.hexie.Main;
import willow.train.hexie.init.track.TrackBlockInit;
import willow.train.hexie.init.track.TrackMaterialInit;

/** RailX's hidden placement variants retain our materials and original track items. */
public final class RailxTracks {
    public static final BlockEntry<RailxTrackBlock> STANDARD = make(TrackMaterialInit.STANDARD, TrackBlockInit.STANDARD_TRACK);
    public static final BlockEntry<RailxTrackBlock> BALLASTLESS = make(TrackMaterialInit.BALLASTLESS, TrackBlockInit.BALLASTLESS_TRACK);
    public static final BlockEntry<RailxTrackBlock> TIELESS = make(TrackMaterialInit.TIELESS, TrackBlockInit.TIELESS_TRACK);

    private RailxTracks() {}

    private static BlockEntry<RailxTrackBlock> make(TrackMaterial material, BlockEntry<TrackBlock> original) {
        var id = Main.asResource("railx_" + material.resourceName() + "_track");
        var entry = Main.registrate().block(id.getPath(), properties -> new RailxTrackBlock(properties, material))
                .initialProperties(() -> Blocks.STONE)
                .properties(properties -> properties.mapColor(MapColor.METAL)
                        .strength(.8f).sound(SoundType.METAL).noOcclusion().forceSolidOn())
                .blockstate((context, provider) -> provider.simpleBlock(context.getEntry(),
                        provider.models().getExistingFile(Main.asResource(
                                "block/track/generated/" + material.resourceName() + "/x_ortho"))))
                .loot((provider, block) -> provider.dropOther(block, original.get()))
                // The block shares the original description ID; do not regenerate its translation.
                .setData(ProviderType.LANG, (context, provider) -> {})
                .setData(ProviderType.BLOCK_TAGS, (context, provider) -> {
                    // These resources also load without RailX, when the hidden blocks are absent.
                    provider.addTag(AllTags.AllBlockTags.TRACKS.tag).addOptional(id);
                    provider.addTag(AllTags.AllBlockTags.GIRDABLE_TRACKS.tag).addOptional(id);
                    provider.addTag(BlockTags.MINEABLE_WITH_PICKAXE).addOptional(id);
                    provider.addTag(Tags.Blocks.RELOCATION_NOT_SUPPORTED).addOptional(id);
                })
                .register();
        FlexiTrackMaterial.INSTANCE.addMaterial(original, entry);
        return entry;
    }

    public static void register(IEventBus bus) {
        // Resolve RailX's registry only after both mods have registered their block entities.
        bus.addListener(RailxTracks::addValidBlocks);
    }

    private static void addValidBlocks(BlockEntityTypeAddBlocksEvent event) {
        event.modify(AllBlockEntityTypes.INSTANCE.getFlexiTrack().get(),
                STANDARD.get(), BALLASTLESS.get(), TIELESS.get());
    }
}
