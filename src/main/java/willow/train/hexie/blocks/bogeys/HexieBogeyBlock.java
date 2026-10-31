package willow.train.hexie.blocks.bogeys;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import com.simibubi.create.content.trains.track.TrackMaterial;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.init.track.TrackMaterialInit;

public abstract class HexieBogeyBlock<T extends AbstractBogeyBlockEntity> extends AbstractBogeyBlock<T> {
    /** The floor owns the block/collision; neither top nor the swivelling frame moves this footprint. */
    @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
            net.minecraft.world.level.BlockGetter world, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        double height = world instanceof willow.train.hexie.vehicle.assembly.CarriageFloorView ? 0
                : world.getBlockEntity(pos) instanceof HexieBogeyBlockEntity be && !be.isCarriageView()
                ? BogeyFloorData.offset(BogeyFloorData.get(be)) : 0;
        return net.minecraft.world.phys.shapes.Shapes.block().move(0, height, 0);
    }
    @Override public net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(BlockState state,
            net.minecraft.world.level.BlockGetter world, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return getShape(state, world, pos, context);
    }
    protected HexieBogeyBlock(Properties properties, BogeySizes.BogeySize size) {
        super(properties.dynamicShape(), size);
    }

    @Override
    public final TrackMaterial.TrackType getTrackType(BogeyStyle style) {
        return TrackMaterialInit.HEXIE_STANDARD;
    }

    @Override
    public final double getWheelPointSpacing() {
        return 2.5;
    }

    @Override
    public final double getWheelRadius() {
        return 0.915;
    }

    @Override
    protected final Vec3 getConnectorAnchorOffset() {
        return new Vec3(0, 7.0 / 32.0, 1);
    }

    @Override
    public final ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return AllBlocks.RAILWAY_CASING.asStack();
    }

    @Override
    public final BlockState getRotatedBlockState(BlockState state, Direction targetedFace) {
        return state;
    }
}
