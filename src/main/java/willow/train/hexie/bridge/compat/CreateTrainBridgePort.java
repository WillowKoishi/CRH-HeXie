package willow.train.hexie.bridge.compat;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.api.contraption.transformable.TransformableBlockEntity;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import com.simibubi.create.content.trains.signal.SignalBlockEntity;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour;
import com.tterrag.registrate.util.entry.BlockEntry;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.bus.api.IEventBus;
import willow.train.hexie.bridge.vehicle.BogeyRegistryBridge.BuiltinBogeyModel;
import willow.train.hexie.bridge.world.BlockEntityViews.CRH2HeadEntityView;
import willow.train.hexie.bridge.world.BlockEntityViews.HexieBogeyBlockEntityView;
import willow.train.hexie.bridge.world.WorldBlockBridge;
import willow.train.hexie.core.api.bogey.BogeyDefinition;
import willow.train.hexie.core.api.bogey.BogeyHandle;
import willow.train.hexie.core.api.railway.RailwayDefinition;
import willow.train.hexie.core.api.railway.RailwayHandle;
import willow.train.hexie.core.util.general.CreativeTabGroup;

public interface CreateTrainBridgePort {
    CreateHeadBogeyOperations headBogey();

    interface CreateHeadBogeyOperations {
        BooleanProperty headMountedProperty();
        boolean isHeadMountedBogey(BlockState state);
        String defaultBogeyStyleId();
        boolean trackEnables(Level level, BlockPos root, Direction facing);
        void migrateLegacy(Level level, BlockPos root, CRH2HeadEntityView head, Direction facing);
        void migrateSchematic(Object level);
    }

    Block createBuiltinBogeyBlock(BlockBehaviour.Properties properties, BuiltinBogeyModel model);

    enum BogeyEntityFamily { STANDARD, DF11G, SS7D, CRH }

    BlockEntityType.BlockEntitySupplier<BlockEntity> bogeyEntityFactory(BogeyEntityFamily family,
            Supplier<BlockEntityType<BlockEntity>> type);

    BogeySelectionCatalogOperations bogeySelectionCatalog();
    BogeySizesInitOperations bogeySizesInit();
    BogeyStylesInitOperations bogeyStylesInit();

    interface BogeySelectionCatalogOperations {
        List<? extends BogeySelectionCatalogEntryView> entries();
    }

    interface BogeySizesInitOperations {
        BogeySizes.BogeySize backward();
        BogeySizes.BogeySize ss7dFront();
        BogeySizes.BogeySize pk209p();
        BogeySizes.BogeySize pk209pNoMotor();
        BogeySizes.BogeySize pk209pBackward();
        BogeySizes.BogeySize sw220k();
        BogeySizes.BogeySize sw220kBackward();
        BogeySizes.BogeySize qj();
        BogeySizes.BogeySize qjBackward();
        BogeySizes.BogeySize sw160();
        BogeySizes.BogeySize dt206();
        BogeySizes.BogeySize sktb200();
        BogeySizes.BogeySize crh1aMotor();
        BogeySizes.BogeySize crh1aTrailer();
    }

    interface BogeyStylesInitOperations {
        BogeyStyle hxstandardbogey();
        BogeyStyle locobogey();
        BogeyStyle steambogey();
        BogeyStyle crhbogey();
    }

    interface BogeySelectionCatalogEntryView {
        CreateBogeyBlockView block();
        String category();
        Component name();
        BogeyStyle style();
        BogeySizes.BogeySize size();
        ResourceLocation id();
    }

    void appendCreativeTabItems(CreativeTabGroup group, Set<ResourceLocation> added, CreativeModeTab.Output output);

    CreateBogeyBackendOperations createBogeyBackend();

    interface CreateBogeyBackendOperations {
        BogeyHandle register(IEventBus bus, ResourceLocation id, BogeyDefinition definition);
    }

    CreateConsoleTimetableOperations createConsoleTimetable();

    interface CreateConsoleTimetableOperations {
        MovingInteractionBehaviour create();
        boolean supports(Block block);
    }

    CreateRailwayBackendOperations createRailwayBackend();

    interface CreateRailwayBackendOperations {
        TrackMaterial material(ResourceLocation id, RailwayDefinition definition);
        boolean supports(TrackMaterial material);
        RailwayDefinition.Bed bed(TrackMaterial material);
        double surfaceHeight(TrackMaterial material);
        double heightIncrease(TrackMaterial material);
        BlockEntry<TrackBlock> blockEntry(RailwayHandle handle);
        RailwayHandle register(IEventBus bus, ResourceLocation id, RailwayDefinition definition);
    }

    CreateRailwayTrackTypeOperations createRailwayTrackType();

    interface CreateRailwayTrackTypeOperations {
        TrackMaterial.TrackType standard();
    }

    CreateSignalAspectsOperations createSignalAspects();

    interface CreateSignalAspectsOperations {
        SignalBlockEntity.SignalState visibleState(SignalBlockEntity be, SignalBlockEntity.SignalState nativeState);
    }

    interface CreateBogeyBlockView extends WorldBlockBridge.HexieBogeyBlockView {
        TrackMaterial.TrackType getTrackType(BogeyStyle style);
    }

    interface RegisteredBogeyBlockView extends WorldBlockBridge.HexieBogeyBlockView {
        default Block asBlock() { return (Block) this; }
    }

    interface DF11GBogeyBlockEntityView extends HexieBogeyBlockEntityView {
        CompoundTag getBogeyData();
    }

    interface AdjustableSignalBlockEntityView extends ThroughSignalBlockEntityView {
        SignalOrientationBehaviourView orientation();
        void orientation(SignalOrientationBehaviourView value);
    }

    interface ThroughSignalBlockEntityView extends TransformableBlockEntity {
        int getLightMode();
        SignalBlockEntity.SignalState getState();
        SignalBlockEntity.OverlayState getOverlay();
        default BlockEntity asBlockEntity() { return (BlockEntity) this; }
    }

    interface SignalOrientationBehaviourView extends ValueSettingsBehaviour {
        boolean isTrackAligned();
        float getModelAngle();
    }
}
