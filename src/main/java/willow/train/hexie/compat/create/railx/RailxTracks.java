package willow.train.hexie.compat.create.railx;

import com.lhwdev.minecraft.railx.flexiTrack.FlexiTrackMaterial;
import com.lhwdev.minecraft.railx.flexiTrack.FlexiTrackBlock;
import com.lhwdev.minecraft.railx.registry.AllBlockEntityTypes;
import com.simibubi.create.AllTags;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.providers.ProviderType;
import com.tterrag.registrate.util.entry.BlockEntry;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;

/** RailX's hidden placement variants retain our materials and original track items. */
public final class RailxTracks {
    private static final Map<ResourceLocation, Entry> ENTRIES = new LinkedHashMap<>();

    /** Internal metadata for generated resources; suppliers remain unresolved until registry events. */
    public record Entry(ResourceLocation materialId, ResourceLocation originalBlockId,
                        BlockEntry<RailxTrackBlock> block) {}

    private RailxTracks() {}

    /** The placed block is authoritative even when RailX connected it without the flexible key. */
    public static boolean isFlexible(BlockState state) {
        return state.getBlock() instanceof FlexiTrackBlock;
    }

    /** Called by the active backend for every registered HeXie railway when RailX is installed. */
    public static synchronized void addTrack(CreateRegistrate registrate, TrackMaterial material,
                                            BlockEntry<TrackBlock> original) {
        var id = ResourceLocation.fromNamespaceAndPath(material.id.getNamespace(),
                "railx_" + material.resourceName() + "_track");
        if (ENTRIES.containsKey(id)) throw new IllegalArgumentException("Duplicate RailX railway: " + id);
        var entry = registrate.block(id.getPath(), properties -> new RailxTrackBlock(properties, material))
                .initialProperties(() -> Blocks.STONE)
                .properties(properties -> properties.mapColor(MapColor.METAL)
                        .strength(.8f).sound(SoundType.METAL).noOcclusion().forceSolidOn())
                .blockstate((context, provider) -> provider.simpleBlock(context.getEntry(),
                        provider.models().getExistingFile(ResourceLocation.fromNamespaceAndPath(
                                material.id.getNamespace(),
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
        ENTRIES.put(id, new Entry(material.id, original.getId(), entry));
    }

    public static synchronized Map<ResourceLocation, Entry> entries() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(ENTRIES));
    }

    public static void register(IEventBus bus) {
        // Resolve RailX's registry only after both mods have registered their block entities.
        bus.addListener(RailxTracks::addValidBlocks);
    }

    private static void addValidBlocks(BlockEntityTypeAddBlocksEvent event) {
        event.modify(AllBlockEntityTypes.INSTANCE.getFlexiTrack().get(),
                entries().values().stream().map(entry -> entry.block().get()).toArray(Block[]::new));
    }
}
