package willow.train.hexie.world.blocks.locos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import willow.train.hexie.bridge.world.CarriageBlockBridge;
import willow.train.hexie.world.blockinteract.LocoBlockInteract;
import willow.train.hexie.world.blocks.parts.ModelRenderBounds;

public final class SS7DCenterCarportBlock extends SS7DCarportBlock implements ModelRenderBounds, CarriageBlockBridge.SS7DCenterCarportBlockView {
    @Override public BlockState cycleAppearance(BlockState state) { return state.cycle(APPEARANCE); }

    public enum Appearance implements StringRepresentable {
        PLAIN("plain"),
        EQUIPMENT_1("equipment_1"),
        EQUIPMENT_2("equipment_2"),
        VENT("vent"),
        CABLES("cables");

        private final String name;

        Appearance(String name) {
            this.name = name;
        }

        @Override public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<SS7DCenterCarportBlock.Appearance> APPEARANCE = EnumProperty.create("appearance", SS7DCenterCarportBlock.Appearance.class);
    public static final EnumProperty<DoorHingeSide> SIDE = BlockStateProperties.DOOR_HINGE;

    public SS7DCenterCarportBlock(BlockBehaviour.Properties properties) {
        super(properties, false);
        registerDefaultState(defaultBlockState().setValue(APPEARANCE, SS7DCenterCarportBlock.Appearance.PLAIN)
                .setValue(SIDE, DoorHingeSide.LEFT));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(APPEARANCE, SIDE);
    }

    @Override public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        return LocoBlockInteract.ForSS7DCenterCarportBlock.useItemOn(stack, state, level, pos, player, hand, hit, () -> super.useItemOn(stack, state, level, pos, player, hand, hit));
    }

    @Override public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        return LocoBlockInteract.ForSS7DCenterCarportBlock.useWithoutItem(state, level, pos, player, hit);
    }

    /** Old captured carriages predate one or both of these additive roof properties. */
    public static CompoundTag upgradeSnapshotProperties(CompoundTag properties) {
        if (properties.contains(APPEARANCE.getName()) && properties.contains(SIDE.getName())) return properties;
        var upgraded = properties.copy();
        if (!upgraded.contains(APPEARANCE.getName()))
            upgraded.putString(APPEARANCE.getName(), SS7DCenterCarportBlock.Appearance.PLAIN.getSerializedName());
        if (!upgraded.contains(SIDE.getName()))
            upgraded.putString(SIDE.getName(), DoorHingeSide.LEFT.getSerializedName());
        return upgraded;
    }

    @Override public AABB modelRenderBounds(BlockState state) {
        // Include both reflections of the long cable variant even before it is selected.
        AABB north = new AABB(-7, 0, -1, 8, 1.5, 2);
        Direction facing = state.getValue(FACING);
        return switch (facing) {
            case EAST -> new AABB(1 - north.maxZ, north.minY, north.minX,
                    1 - north.minZ, north.maxY, north.maxX);
            case SOUTH -> new AABB(1 - north.maxX, north.minY, 1 - north.maxZ,
                    1 - north.minX, north.maxY, 1 - north.minZ);
            case WEST -> new AABB(north.minZ, north.minY, 1 - north.maxX,
                    north.maxZ, north.maxY, 1 - north.minX);
            default -> north;
        };
    }
}
