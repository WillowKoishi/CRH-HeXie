package willow.train.hexie.world.blocks.emu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.CarriageBlockBridge;
import willow.train.hexie.world.blockinteract.EmuBlockInteract;
import willow.train.hexie.world.blockinteract.TestCabInteraction;
import willow.train.hexie.world.blocks.misc.DrivingConsoleBlock;
import willow.train.hexie.world.collision.EmuCollision;

/** Temporary one-block fixture; the production cab model is deliberately separate. */
public final class TestDrivingConsoleBlock extends DrivingConsoleBlock implements CarriageBlockBridge.TestDrivingConsoleBlockView {
    public TestDrivingConsoleBlock(BlockBehaviour.Properties properties) {
        super(properties, TestCabInteraction::surface, HexieBridges.cabControl().testCabControls().instance(), HexieBridges.cabControl().testCabControls().neutralPositions());
    }
    @Override public AABB controlBounds(BlockState state) {
        return EmuBlockInteract.ForTestDrivingConsoleBlock.controlBounds(this, state);
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return EmuCollision.ForTestDrivingConsoleBlock.getShape(state, level, pos, context);
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return EmuCollision.ForTestDrivingConsoleBlock.getCollisionShape(this, state, level, pos, context);
    }
}
