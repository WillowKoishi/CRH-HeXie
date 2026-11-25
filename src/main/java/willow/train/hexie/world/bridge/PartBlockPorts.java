package willow.train.hexie.world.bridge;

import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.world.BlockEntityViews;
import willow.train.hexie.bridge.world.PartBlockBridge;
import willow.train.hexie.bridge.world.WorldBlockBridge;
import willow.train.hexie.world.blockinteract.CRH1DoorInteraction;
import willow.train.hexie.world.blockinteract.CRH2DoorInteraction;
import willow.train.hexie.world.blockinteract.CouplerInteraction;
import willow.train.hexie.world.blockinteract.HeadNumberInteraction;
import willow.train.hexie.world.blockinteract.PanelAttachmentHit;
import willow.train.hexie.world.blockinteract.PanelAttachmentRemoval;
import willow.train.hexie.world.blockinteract.PassengerDisplayInstallation;
import willow.train.hexie.world.blocks.parts.LegacyPartLayout;
import willow.train.hexie.world.blocks.parts.PanelText;
import willow.train.hexie.world.blocks.parts.PartDefinitions;
import willow.train.hexie.world.blocks.parts.PartStructures;
import willow.train.hexie.world.blocks.parts.VariantPartBlock;
import willow.train.hexie.world.placement.PartInstallation;
import willow.train.hexie.world.placement.TrainPanelPlacement;

final class PartBlockPorts implements PartBlockBridge {
    private static final CRH1DoorInteractionOperations CRH1_DOOR_INTERACTION = CRH1DoorInteraction::validExtendedHit;
    @Override public CRH1DoorInteractionOperations crh1DoorInteraction() { return CRH1_DOOR_INTERACTION; }

    private static final CRH2DoorInteractionOperations CRH2_DOOR_INTERACTION = CRH2DoorInteraction::validExtendedHit;
    @Override public CRH2DoorInteractionOperations crh2DoorInteraction() { return CRH2_DOOR_INTERACTION; }

    private static final CouplerInteractionOperations COUPLER_INTERACTION = new CouplerInteractionOperations() {
        @Override public WorldBlockBridge.CouplerGeometryHitView classifyHit(Level level, BlockPos position, BlockState state, BlockHitResult requested) {
            return CouplerInteraction.classifyHit(level, position, state, requested);
        }
        @Override public boolean validExtendedHit(ServerPlayer player, BlockHitResult requested) {
            return CouplerInteraction.validExtendedHit(player, requested);
        }
        @Override public WorldBlockBridge.CouplerGeometryHitView clipWorld(BlockState state, BlockPos position, float progress, BlockEntityViews.CouplerBlockEntityView entity, float partial, Vec3 from, Vec3 to) {
            return CouplerInteraction.clipWorld(state, position, progress, entity, partial, from, to);
        }
        @Override public Vec3 toModel(BlockState state, BlockPos position, Vec3 point) {
            return CouplerInteraction.toModel(state, position, point);
        }
        @Override public WorldBlockBridge.CouplerGeometryHitView fromModel(BlockState state, BlockPos position, WorldBlockBridge.CouplerGeometryHitView hit) {
            return CouplerInteraction.fromModel(state, position, hit);
        }
        @Override public boolean hasGeometry(BlockState state) {
            return CouplerInteraction.hasGeometry(state);
        }
        @Override public float coverProgress(BlockState state) {
            return CouplerInteraction.coverProgress(state);
        }
    };
    @Override public CouplerInteractionOperations couplerInteraction() { return COUPLER_INTERACTION; }

    private static final HeadNumberInteractionOperations HEAD_NUMBER_INTERACTION = new HeadNumberInteractionOperations() {
        @Override public BlockPos pick(Level level, Player player) {
            return HeadNumberInteraction.pick(level, player);
        }
        @Override public InteractionResult open(Level level, Player player, BlockPos root) {
            return HeadNumberInteraction.open(level, player, root);
        }
    };
    @Override public HeadNumberInteractionOperations headNumberInteraction() { return HEAD_NUMBER_INTERACTION; }

    @Override public PanelExtension panelExtension(BlockState state) {
        return PanelExtension.valueOf(PartDefinitions.get(state).extension().name());
    }

    @Override public boolean panelRunsAlongFacing(BlockState state) {
        return PartDefinitions.get(state).runAxis() == PartDefinitions.RunAxis.FACING;
    }

    @Override public BlockState alignPanelForPlacement(BlockState placed, BlockState source) {
        return LegacyPartLayout.placed(TrainPanelPlacement.align(placed, source));
    }

    private static final LegacyPartLayoutOperations LEGACY_PART_LAYOUT = new LegacyPartLayoutOperations() {
        @Override public BooleanProperty occupied() {
            return LegacyPartLayout.OCCUPIED;
        }
        @Override public BlockState placed(BlockState state) {
            return LegacyPartLayout.placed(state);
        }
        @Override public boolean supports(BlockState state) {
            return LegacyPartLayout.supports(state);
        }
    };
    @Override public LegacyPartLayoutOperations legacyPartLayout() { return LEGACY_PART_LAYOUT; }

    private static final PanelAttachmentHitOperations PANEL_ATTACHMENT_HIT = new PanelAttachmentHitOperations() {
        @Override public PanelAttachmentHitTargetView resolve(BlockGetter level, BlockPos clicked, BlockHitResult hit) {
            return PanelAttachmentHit.resolve(level, clicked, hit);
        }
        @Override public PanelAttachmentHitTargetView emptyMarkingTarget(BlockGetter level, BlockPos clicked, BlockHitResult hit) {
            return PanelAttachmentHit.emptyMarkingTarget(level, clicked, hit);
        }
    };
    @Override public PanelAttachmentHitOperations panelAttachmentHit() { return PANEL_ATTACHMENT_HIT; }

    private static final PanelAttachmentRemovalOperations PANEL_ATTACHMENT_REMOVAL = PanelAttachmentRemoval::remove;
    @Override public PanelAttachmentRemovalOperations panelAttachmentRemoval() { return PANEL_ATTACHMENT_REMOVAL; }

    private static final PanelTextOperations PANEL_TEXT = new PanelTextOperations() {
        @Override public PanelTextView create(String text, float size, int x, int y, String font, int spacing) {
            return new PanelText(text, size, x, y, font, spacing);
        }
        @Override public PanelTextView create(FriendlyByteBuf buf) {
            return new PanelText(buf);
        }
        @Override public PanelTextView empty() {
            return PanelText.EMPTY;
        }
        @Override public PanelTextView load(CompoundTag tag) {
            return PanelText.load(tag);
        }
    };
    @Override public PanelTextOperations panelText() { return PANEL_TEXT; }

    private static final PartInstallationOperations PART_INSTALLATION = new PartInstallationOperations() {
        @Override public boolean mutating() {
            return PartInstallation.mutating();
        }
        @Override public boolean available(Level level, Map<BlockPos, BlockState> cells, Predicate<BlockPos> mayReplace, Predicate<BlockPos> permission) {
            return PartInstallation.available(level, cells, mayReplace, permission);
        }
        @Override public boolean install(Level level, Map<BlockPos, BlockState> cells, Predicate<BlockPos> mayReplace, Predicate<BlockPos> permission) {
            return PartInstallation.install(level, cells, mayReplace, permission);
        }
        @Override public boolean replace(Level level, Map<BlockPos, BlockState> previous, Map<BlockPos, BlockState> cells, Predicate<BlockPos> mayReplace, Predicate<BlockPos> permission, Runnable installed) {
            return PartInstallation.replace(level, previous, cells, mayReplace, permission, installed);
        }
        @Override public boolean complete(Level level, BlockPos root, BlockState state, PartStructureView part) {
            return PartInstallation.complete(level, root, state, part);
        }
        @Override public void removeOtherCells(Level level, BlockPos removed, Map<BlockPos, BlockState> cells) {
            PartInstallation.removeOtherCells(level, removed, cells);
        }
    };
    @Override public PartInstallationOperations partInstallation() { return PART_INSTALLATION; }

    private static final PartStructuresOperations PART_STRUCTURES = new PartStructuresOperations() {
        @Override public PartStructuresRootView resolve(BlockGetter level, BlockPos pos) {
            return PartStructures.resolve(level, pos);
        }
        @Override public PartStructuresRootView resolve(BlockGetter level, BlockPos pos, BlockState state) {
            return PartStructures.resolve(level, pos, state);
        }
    };
    @Override public PartStructuresOperations partStructures() { return PART_STRUCTURES; }

    private static final PassengerDisplayInstallationOperations PASSENGER_DISPLAY_INSTALLATION = PassengerDisplayInstallation::install;
    @Override public PassengerDisplayInstallationOperations passengerDisplayInstallation() { return PASSENGER_DISPLAY_INSTALLATION; }

    private static final VariantPartBlockOperations VARIANT_PART_BLOCK = () -> VariantPartBlock.ALTERNATE;
    @Override public VariantPartBlockOperations variantPartBlock() { return VARIANT_PART_BLOCK; }
}
