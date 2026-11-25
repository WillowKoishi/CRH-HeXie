package willow.train.hexie.bridge.world;

import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.core.api.interaction.HexieItemRequirementProvider;
import willow.train.hexie.core.api.interaction.HexieWrenchable;

public interface PartBlockBridge {
    CRH1DoorInteractionOperations crh1DoorInteraction();

    interface CRH1DoorInteractionOperations {
        boolean validExtendedHit(ServerPlayer player, BlockHitResult hit);
    }

    CRH2DoorInteractionOperations crh2DoorInteraction();

    interface CRH2DoorInteractionOperations {
        boolean validExtendedHit(ServerPlayer player, BlockHitResult hit);
    }

    CouplerInteractionOperations couplerInteraction();

    interface CouplerInteractionOperations {
        WorldBlockBridge.CouplerGeometryHitView classifyHit(Level level, BlockPos position, BlockState state,
                BlockHitResult requested);
        boolean validExtendedHit(ServerPlayer player, BlockHitResult requested);
        WorldBlockBridge.CouplerGeometryHitView clipWorld(BlockState state, BlockPos position, float progress,
                BlockEntityViews.CouplerBlockEntityView entity, float partial, Vec3 from, Vec3 to);
        Vec3 toModel(BlockState state, BlockPos position, Vec3 point);
        WorldBlockBridge.CouplerGeometryHitView fromModel(BlockState state, BlockPos position,
                WorldBlockBridge.CouplerGeometryHitView hit);
        boolean hasGeometry(BlockState state);
        float coverProgress(BlockState state);
    }

    HeadNumberInteractionOperations headNumberInteraction();

    interface HeadNumberInteractionOperations {
        BlockPos pick(Level level, Player player);
        InteractionResult open(Level level, Player player, BlockPos root);
    }

    @FunctionalInterface
    interface PanelPlacementBackend {
        ItemInteractionResult place(ItemStack stack, BlockState state, Level level, BlockPos pos,
                Player player, InteractionHand hand, BlockHitResult hit);
    }

    enum PanelExtension { NONE, SIDE, FLOOR, ROOF, SKIRT }
    PanelExtension panelExtension(BlockState state);
    boolean panelRunsAlongFacing(BlockState state);
    BlockState alignPanelForPlacement(BlockState placed, BlockState source);

    LegacyPartLayoutOperations legacyPartLayout();

    interface LegacyPartLayoutOperations {
        BooleanProperty occupied();
        BlockState placed(BlockState state);
        boolean supports(BlockState state);
    }

    PanelAttachmentHitOperations panelAttachmentHit();

    interface PanelAttachmentHitOperations {
        PanelAttachmentHitTargetView resolve(BlockGetter level, BlockPos clicked, BlockHitResult hit);
        PanelAttachmentHitTargetView emptyMarkingTarget(BlockGetter level, BlockPos clicked, BlockHitResult hit);
    }

    PanelAttachmentRemovalOperations panelAttachmentRemoval();

    interface PanelAttachmentRemovalOperations {
        InteractionResult remove(UseOnContext context);
    }

    PanelTextOperations panelText();

    interface PanelTextOperations {
        PanelTextView create(String text, float size, int x, int y, String font, int spacing);
        PanelTextView create(FriendlyByteBuf buf);
        PanelTextView empty();
        PanelTextView load(CompoundTag tag);
    }

    PartInstallationOperations partInstallation();

    interface PartInstallationOperations {
        boolean mutating();
        boolean available(Level level, Map<BlockPos, BlockState> cells, Predicate<BlockPos> mayReplace,
                Predicate<BlockPos> permission);
        boolean install(Level level, Map<BlockPos, BlockState> cells, Predicate<BlockPos> mayReplace,
                Predicate<BlockPos> permission);
        boolean replace(Level level, Map<BlockPos, BlockState> previous, Map<BlockPos, BlockState> cells,
                Predicate<BlockPos> mayReplace, Predicate<BlockPos> permission, Runnable installed);
        boolean complete(Level level, BlockPos root, BlockState state, PartStructureView part);
        void removeOtherCells(Level level, BlockPos removed, Map<BlockPos, BlockState> cells);
    }

    PartStructuresOperations partStructures();

    interface PartStructuresOperations {
        PartStructuresRootView resolve(BlockGetter level, BlockPos pos);
        PartStructuresRootView resolve(BlockGetter level, BlockPos pos, BlockState state);
    }

    PassengerDisplayInstallationOperations passengerDisplayInstallation();

    interface PassengerDisplayInstallationOperations {
        InteractionResult install(UseOnContext context, boolean number);
    }

    VariantPartBlockOperations variantPartBlock();

    interface VariantPartBlockOperations {
        EnumProperty<VariantPartBlockAppearance> alternate();
    }

    interface ModelRenderBoundsView {
        AABB modelRenderBounds(BlockState state);
    }

    interface PanelTextView {
        CompoundTag save();
        void write(FriendlyByteBuf buf);
        String text();
        float size();
        int x();
        int y();
        String font();
        int spacing();
    }

    interface PartMemberBlockView extends HexieItemRequirementProvider, HexieWrenchable {
        default Block asBlock() { return (Block) this; }
    }

    interface PartStructureView {
        Map<BlockPos, BlockState> partCells(BlockGetter level, BlockPos root, BlockState rootState);
        BlockPos partRoot(BlockGetter level, BlockPos pos, BlockState state);
        default boolean isPartMember() { return false; }
        default boolean schematicRoot() { return false; }
        default boolean ownsCell(BlockGetter level, BlockPos root, BlockState rootState, BlockPos pos, BlockState expected) {
            return level.getBlockState(pos).equals(expected);
        }
    }

    interface PartStructuresRootView {
        BlockPos pos();
        BlockState state();
        PartStructureView structure();
    }

    interface VariantPartBlockView extends CarriageBlockBridge.CRH2PartBlockView {
        default Block asBlock() { return (Block) this; }
    }

    enum VariantPartBlockAppearance implements StringRepresentable {
        ANTENNA("false"), ANTENNA_2("true"), CABLE("cable"), FLAT("flat");

        private final String serializedName;
        VariantPartBlockAppearance(String serializedName) { this.serializedName = serializedName; }
        @Override public String getSerializedName() { return serializedName; }
    }

    interface VehicleMarkingSurfaceView {
        BlockPos markingRoot(BlockGetter level, BlockPos pos, BlockState state);
        AABB markingBounds(BlockState state);
        AABB markingRenderBounds(BlockState state);
        double markingDepth(double localY);
        double harmonyMarkingOffsetY();
        Direction markingFacing(BlockState state);
        int markingSlots(BlockState state);
        int markingSlot(BlockState state, BlockPos root, Vec3 hit);
    }

    interface VehicleMarkingsView {
        String[] fields();
        boolean descriptionLeft();
        int markingSlot();
        int harmony(int slot);
        PanelTextView panelText(int slot);
        void setPanelText(int slot, PanelTextView text);
        void setHarmony(int slot, int direction);
        void update(String[] fields, boolean descriptionLeft);
        void selectMarkingSlot(int slot);
        void attachHarmony(int slot);
        void toggleHarmony(int slot);
        void toggleDescription();
    }

    enum PanelAttachmentHitKind {
        NONE, SIGN, NUMBER, MARKING_FIELDS, PANEL_TEXT, HARMONY, LEGACY
    }

    interface PanelAttachmentHitTargetView {
        BlockPos pos();
        BlockState state();
        PanelAttachmentHitKind kind();
        int slot();
    }
}
