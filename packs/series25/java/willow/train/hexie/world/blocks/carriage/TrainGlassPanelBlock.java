package willow.train.hexie.world.blocks.carriage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import willow.train.hexie.world.blocks.base.HorizontalGlassBlockBase;
import willow.train.hexie.world.blocks.signs.PassengerDisplayPanel;
import willow.train.hexie.world.collision.CarriageCollisionShapes;

public class TrainGlassPanelBlock extends HorizontalGlassBlockBase implements PassengerDisplayPanel {

public TrainGlassPanelBlock(BlockBehaviour.Properties p_52325_) {
        super(p_52325_);
    }

    public VoxelShape getShape(
            BlockState p_54372_,
            BlockGetter p_54373_,
            BlockPos p_54374_,
            CollisionContext p_54375_) {
        return CarriageCollisionShapes.ForTrainGlassPanelBlock.getShape(p_54372_, p_54373_, p_54374_, p_54375_);
    }

    public @NotNull VoxelShape getVisualShape(
            BlockState p_48735_,
            BlockGetter p_48736_,
            BlockPos p_48737_,
            CollisionContext p_48738_) {
        return CarriageCollisionShapes.ForTrainGlassPanelBlock.getVisualShape(p_48735_, p_48736_, p_48737_, p_48738_);
    }

    public float getShadeBrightness(BlockState p_48731_, BlockGetter p_48732_, BlockPos p_48733_) {
        return 1.0F;
    }

    public boolean propagatesSkylightDown(
            BlockState p_48740_, BlockGetter p_48741_, BlockPos p_48742_) {
        return true;
    }
}
