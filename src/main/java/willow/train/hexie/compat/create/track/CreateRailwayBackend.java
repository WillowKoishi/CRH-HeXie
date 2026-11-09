package willow.train.hexie.compat.create.track;

import static com.simibubi.create.foundation.data.TagGen.pickaxeOnly;

import com.simibubi.create.AllTags;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.simibubi.create.content.trains.track.TrackMaterialFactory;
import com.simibubi.create.content.trains.track.TrackShape;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;
import com.tterrag.registrate.util.entry.BlockEntry;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import willow.train.hexie.Main;
import willow.train.hexie.compat.create.CreateBlockReservations;
import willow.train.hexie.api.railway.RailwayDefinition;
import willow.train.hexie.api.railway.RailwayHandle;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import willow.train.hexie.item.track.StandardTrackItem;
import willow.train.hexie.railway.RailwayBackend;

/** The active backend owns all Create registration and optional compatibility details. */
public final class CreateRailwayBackend implements RailwayBackend {
    private static final Map<ResourceLocation, TrackMaterial> MATERIALS = new HashMap<>();
    private static final Map<TrackMaterial, RailwayDefinition> DEFINITIONS = new IdentityHashMap<>();
    private static final Map<ResourceLocation, BlockEntry<TrackBlock>> BLOCKS = new HashMap<>();
    private static final Map<String, CreateRegistrate> REGISTRATES = new HashMap<>();
    private static final Map<String, IEventBus> BUSES = new HashMap<>();

    /** May be called before block registration to preserve the existing material constants. */
    public static synchronized TrackMaterial material(ResourceLocation id, RailwayDefinition definition) {
        validateMaterial(id, definition);
        var existing = MATERIALS.get(id);
        if (existing != null) return existing;
        var models = definition.models();
        var material = TrackMaterialFactory.make(id).lang(definition.displayName())
                .block(() -> () -> (TrackBlock) BuiltInRegistries.BLOCK.get(definition.blockId()))
                .particle(CreateRailwayResources.particle(models.particle()))
                .trackType(CreateRailwayTrackType.STANDARD).sleeper(Blocks.ANDESITE_SLAB)
                .customModels(() -> () -> PartialModel.of(models.sleeper()),
                        () -> () -> PartialModel.of(models.leftRail()),
                        () -> () -> PartialModel.of(models.rightRail()))
                .build();
        MATERIALS.put(id, material);
        DEFINITIONS.put(material, definition);
        return material;
    }

    private static synchronized void validateMaterial(ResourceLocation id, RailwayDefinition definition) {
        var existing = MATERIALS.get(id);
        if (existing != null) {
            if (!definition.equals(DEFINITIONS.get(existing)))
                throw new IllegalArgumentException("Conflicting railway material: " + id);
        } else if (TrackMaterial.ALL.containsKey(id)) {
            throw new IllegalArgumentException("Railway ID is already owned by another material: " + id);
        }
    }

    public static synchronized boolean supports(TrackMaterial material) {
        return DEFINITIONS.containsKey(material);
    }

    public static synchronized RailwayDefinition.Bed bed(TrackMaterial material) {
        var definition = DEFINITIONS.get(material);
        return definition == null ? RailwayDefinition.Bed.NONE : definition.bed();
    }

    public static synchronized double surfaceHeight(TrackMaterial material) {
        var definition = DEFINITIONS.get(material);
        return definition == null ? RailwayDefinition.STANDARD_SURFACE_HEIGHT : definition.railSurfaceHeight();
    }

    public static double heightIncrease(TrackMaterial material) {
        // Retain the exact existing offset for the built-ins and unregistered users of the shared type.
        return 0.4074 + (surfaceHeight(material) - RailwayDefinition.STANDARD_SURFACE_HEIGHT);
    }

    public static synchronized RailwayDefinition definition(TrackMaterial material) {
        var definition = DEFINITIONS.get(material);
        if (definition == null) throw new IllegalArgumentException("Unregistered HeXie railway: " + material.id);
        return definition;
    }

    /** Internal typed alias for existing built-in block consumers. */
    public static synchronized BlockEntry<TrackBlock> blockEntry(RailwayHandle handle) {
        return java.util.Objects.requireNonNull(BLOCKS.get(handle.id()));
    }

    @Override public synchronized RailwayHandle register(IEventBus bus, ResourceLocation id, RailwayDefinition definition) {
        var owner = ModList.get().getModContainerById(id.getNamespace())
                .orElseThrow(() -> new IllegalArgumentException("Railway namespace is not a loaded mod: " + id.getNamespace()));
        if (owner.getEventBus() != bus)
            throw new IllegalArgumentException("Use the owning mod's event bus to register its railways: " + id);
        // Reject material conflicts before reserving IDs shared with add-on bogey registration.
        validateMaterial(id, definition);
        boolean railx = ModList.get().isLoaded("railx");
        var hiddenId = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "railx_" + id.getPath() + "_track");
        CreateBlockReservations.reserve("railway " + id, railx
                ? java.util.List.of(definition.blockId(), hiddenId) : java.util.List.of(definition.blockId()));
        var registrate = registrate(bus, id.getNamespace());
        var material = material(id, definition);
        var block = registrate.block(definition.blockId().getPath(), material::createBlock)
                .initialProperties(() -> Blocks.STONE)
                .properties(properties -> properties.mapColor(MapColor.METAL)
                        .strength(0.8F).sound(SoundType.METAL).noOcclusion().forceSolidOn())
                .transform(pickaxeOnly())
                .blockstate(CreateRailwayBackend::generateBlockstate)
                .tag(AllTags.AllBlockTags.TRACKS.tag)
                .tag(AllTags.AllBlockTags.GIRDABLE_TRACKS.tag)
                .tag(net.neoforged.neoforge.common.Tags.Blocks.RELOCATION_NOT_SUPPORTED)
                .lang(definition.displayName() + " Train Track")
                .onRegister(CreateRegistrate.blockModel(() ->
                        willow.train.hexie.client.render.track.BallastTrackModel::create))
                .item(StandardTrackItem::new)
                .tag(AllTags.AllItemTags.TRACKS.tag)
                .model((context, provider) -> provider.generated(context, definition.models().itemTexture()))
                .build().register();
        BLOCKS.put(id, block);
        if (railx) {
            willow.train.hexie.compat.create.railx.RailxTracks.addTrack(registrate, material, block);
        }
        return new RailwayHandle(id, definition, block::get, () -> block.get().asItem());
    }

    private static CreateRegistrate registrate(IEventBus bus, String namespace) {
        if (namespace.equals(Main.MOD_ID)) return Main.registrate();
        var previous = BUSES.putIfAbsent(namespace, bus);
        if (previous != null && previous != bus)
            throw new IllegalArgumentException("Railways in one namespace must use the same mod event bus: " + namespace);
        return REGISTRATES.computeIfAbsent(namespace, owner -> {
            var registrate = new RailwayRegistrate(owner);
            registrate.registerEventListeners(bus);
            return registrate;
        });
    }

    /** Does not claim the contributing mod's public Create Registrate callback slot. */
    private static final class RailwayRegistrate extends CreateRegistrate {
        private RailwayRegistrate(String namespace) { super(namespace); }
    }

    private static void generateBlockstate(DataGenContext<Block, TrackBlock> context,
            RegistrateBlockstateProvider provider) {
        var material = context.getEntry().getMaterial();
        var models = definition(material).models();
        provider.getVariantBuilder(context.getEntry()).forAllStates(state -> {
            var shape = state.getValue(TrackBlock.SHAPE);
            ModelFile model;
            if (shape == TrackShape.NONE) {
                model = provider.models().getExistingFile(provider.mcLoc("block/air"));
            } else {
                model = provider.models().withExistingParent(
                        CreateRailwayResources.blockModel(material.id, shape.getModel()).getPath(),
                        models.blocks().get(CreateRailwayResources.shape(shape.getModel())))
                        .texture("particle", CreateRailwayResources.particle(models.particle()));
            }
            return ConfiguredModel.builder().modelFile(model)
                    .rotationY(Math.floorMod(shape.getModelRotation(), 360)).build();
        });
    }
}
