package willow.train.hexie.compat.bridge;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.tterrag.registrate.util.entry.BlockEntry;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import willow.train.hexie.bridge.compat.CreateTrainBridgePort.BogeySelectionCatalogOperations;
import willow.train.hexie.bridge.compat.CreateTrainBridgePort.BogeySizesInitOperations;
import willow.train.hexie.bridge.compat.CreateTrainBridgePort.BogeyStylesInitOperations;
import willow.train.hexie.bridge.compat.CreateTrainBridgePort;
import willow.train.hexie.bridge.vehicle.BogeyRegistryBridge.BuiltinBogeyModel;
import willow.train.hexie.compat.create.block.CreateCreativeTabItems;
import willow.train.hexie.compat.create.bogey.BogeySelectionCatalog;
import willow.train.hexie.compat.create.bogey.BuiltinBogeyBlocks;
import willow.train.hexie.compat.create.bogey.CreateBogeyBackend;
import willow.train.hexie.compat.create.bogey.CreateHeadBogeyOps;
import willow.train.hexie.compat.create.signal.CreateConsoleTimetable;
import willow.train.hexie.compat.create.signal.CreateSignalAspects;
import willow.train.hexie.compat.create.track.CreateRailwayBackend;
import willow.train.hexie.compat.create.track.CreateRailwayTrackType;
import willow.train.hexie.core.api.railway.RailwayDefinition;
import willow.train.hexie.core.api.railway.RailwayHandle;
import willow.train.hexie.core.util.general.CreativeTabGroup;

final class CreateTrainPorts implements CreateTrainBridgePort {
    @Override public CreateHeadBogeyOperations headBogey() {
        return CreateHeadBogeyOps.INSTANCE;
    }

    @Override public Block createBuiltinBogeyBlock(BlockBehaviour.Properties properties, BuiltinBogeyModel model) {
        return BuiltinBogeyBlocks.create(properties, model);
    }

    @Override public BlockEntityType.BlockEntitySupplier<BlockEntity> bogeyEntityFactory(BogeyEntityFamily family,
            Supplier<BlockEntityType<BlockEntity>> type) {
        return BuiltinBogeyBlocks.entityFactory(family, type);
    }

    @Override
    public BogeySelectionCatalogOperations bogeySelectionCatalog() {
        return BogeySelectionCatalog::entries;
    }

    @Override
    public BogeySizesInitOperations bogeySizesInit() {
        return CreateBogeyRegistryPort.INSTANCE;
    }

    @Override
    public BogeyStylesInitOperations bogeyStylesInit() {
        return CreateBogeyRegistryPort.INSTANCE;
    }

    @Override public void appendCreativeTabItems(CreativeTabGroup group, Set<ResourceLocation> added, CreativeModeTab.Output output) {
        CreateCreativeTabItems.append(group, added, output);
    }

    private final CreateConsoleTimetableOperations createConsoleTimetable = new CreateConsoleTimetableOperations() {
        @Override public MovingInteractionBehaviour create() {
            return new CreateConsoleTimetable();
        }

        @Override public boolean supports(Block block) {
            return CreateConsoleTimetable.supports(block);
        }
    };

    private final CreateRailwayBackendOperations createRailwayBackend = new CreateRailwayBackendOperations() {
        @Override public TrackMaterial material(ResourceLocation id, RailwayDefinition definition) {
            return CreateRailwayBackend.material(id, definition);
        }

        @Override public boolean supports(TrackMaterial material) {
            return CreateRailwayBackend.supports(material);
        }

        @Override public RailwayDefinition.Bed bed(TrackMaterial material) {
            return CreateRailwayBackend.bed(material);
        }

        @Override public double surfaceHeight(TrackMaterial material) {
            return CreateRailwayBackend.surfaceHeight(material);
        }

        @Override public double heightIncrease(TrackMaterial material) {
            return CreateRailwayBackend.heightIncrease(material);
        }

        @Override public BlockEntry<TrackBlock> blockEntry(RailwayHandle handle) {
            return CreateRailwayBackend.blockEntry(handle);
        }

        @Override public RailwayHandle register(IEventBus bus, ResourceLocation id, RailwayDefinition definition) {
            return CreateRailwayBackend.register(bus, id, definition);
        }
    };

    @Override public CreateBogeyBackendOperations createBogeyBackend() {
        return CreateBogeyBackend::register;
    }

    @Override public CreateConsoleTimetableOperations createConsoleTimetable() {
        return createConsoleTimetable;
    }

    @Override public CreateRailwayBackendOperations createRailwayBackend() {
        return createRailwayBackend;
    }

    @Override public CreateRailwayTrackTypeOperations createRailwayTrackType() {
        return () -> CreateRailwayTrackType.STANDARD;
    }

    @Override public CreateSignalAspectsOperations createSignalAspects() {
        return CreateSignalAspects::visibleState;
    }
}
