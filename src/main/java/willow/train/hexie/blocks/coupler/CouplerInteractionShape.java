package willow.train.hexie.blocks.coupler;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.mixins.mixin.VoxelShapeAccessor;

/** Keeps the existing outline boxes while clipping native rays against the authored surfaces. */
final class CouplerInteractionShape extends VoxelShape {
    private final VoxelShape outline;
    private final BlockState state;
    private final float progress;
    private final double floorOffset;
    private final willow.train.hexie.blockentity.CouplerBlockEntity entity;

    CouplerInteractionShape(VoxelShape outline, BlockState state, float progress, double floorOffset) {
        this(outline, state, progress, floorOffset, null);
    }

    CouplerInteractionShape(VoxelShape outline, BlockState state, float progress, double floorOffset,
            willow.train.hexie.blockentity.CouplerBlockEntity entity) {
        super(((VoxelShapeAccessor) outline).hexie$getShape());
        this.outline = outline;
        this.state = state;
        this.progress = progress;
        this.floorOffset = floorOffset;
        this.entity = entity;
    }

    @Override public DoubleList getCoords(Direction.Axis axis) {
        return outline.getCoords(axis);
    }

    @Override public BlockHitResult clip(Vec3 from, Vec3 to, BlockPos position) {
        var hit = CouplerInteraction.clipWorld(state, position, progress, entity, 1,
                from.add(0, floorOffset, 0), to.add(0, floorOffset, 0));
        return hit == null ? null : new BlockHitResult(hit.point().add(0, -floorOffset, 0),
                hit.face(), position, false);
    }
}
