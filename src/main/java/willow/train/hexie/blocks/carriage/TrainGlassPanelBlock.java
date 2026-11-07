package willow.train.hexie.blocks.carriage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.NotNull;

import willow.train.hexie.blocks.base.HorizontalGlassBlockBase;

public class TrainGlassPanelBlock extends HorizontalGlassBlockBase implements willow.train.hexie.blocks.signs.PassengerDisplayPanel {
protected static final VoxelShape SOUTH_AABB = Block.box(0.0D, 0.0D, 15, 16, 16, 16);
protected static final VoxelShape NORTH_AABB = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 1);
public TrainGlassPanelBlock(Properties p_52325_) {
        super(p_52325_);
    }

    public VoxelShape getShape(
            BlockState p_54372_,
            BlockGetter p_54373_,
            BlockPos p_54374_,
            CollisionContext p_54375_) {
        return willow.train.hexie.blocks.parts.PartShapeDefinitions.get("edge_panel_1",p_54372_.getValue(FACING));
    }

    public @NotNull VoxelShape getVisualShape(
            BlockState p_48735_,
            BlockGetter p_48736_,
            BlockPos p_48737_,
            CollisionContext p_48738_) {
        return Shapes.empty();
    }

    public float getShadeBrightness(BlockState p_48731_, BlockGetter p_48732_, BlockPos p_48733_) {
        return 1.0F;
    }

    public boolean propagatesSkylightDown(
            BlockState p_48740_, BlockGetter p_48741_, BlockPos p_48742_) {
        return true;
    }
}
