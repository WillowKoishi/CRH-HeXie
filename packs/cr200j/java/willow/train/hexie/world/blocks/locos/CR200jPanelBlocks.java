package willow.train.hexie.world.blocks.locos;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.world.blocks.base.SideMirrorBlockBase;
import willow.train.hexie.world.blocks.signs.PassengerDisplayPanel;
import willow.train.hexie.world.collision.LocoCollision;

/** Existing registration/API backed by shared, explicit geometry definitions. */
public class CR200jPanelBlocks extends SideMirrorBlockBase implements PassengerDisplayPanel {
    protected int module_type;
    public CR200jPanelBlocks(BlockBehaviour.Properties properties,int type) { super(properties);module_type=type; }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return LocoCollision.ForCR200jPanelBlocks.getShape(this, state, level, pos, context);
    }

    public final int moduleType() { return module_type; }
}
