package willow.train.hexie.world.placement;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.world.blocks.base.HorizontalBlockBase;
import willow.train.hexie.world.blocks.base.SideMirrorBlockBase;
import willow.train.hexie.world.blocks.emu.CRH1EndFaceBlock;
import willow.train.hexie.world.blocks.emu.CRH1HeadBlock;
import willow.train.hexie.world.blocks.emu.CRH2DoorBlock;
import willow.train.hexie.world.blocks.emu.CRH2EndFaceBlock;
import willow.train.hexie.world.blocks.emu.CRH2HeadBlock;
import willow.train.hexie.world.blocks.emu.CRH2PanelBlock;
import willow.train.hexie.world.blocks.emu.CRH2PartBlock;
import willow.train.hexie.world.blocks.emu.HeadBogey;

public final class EmuPlacement {
    private EmuPlacement() {}

    public static final class ForCRH1DoorBlock {
        public static BlockState getStateForPlacement(BlockPlaceContext context, Supplier<BlockState> baseGetStateForPlacement) {
            BlockState state = baseGetStateForPlacement.get();
            if (state == null) return null;
            Direction face = context.getClickedFace();
            Direction facing = face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite();
            Direction right = facing.getClockWise();
            Vec3 hit = context.getClickLocation();
            BlockPos pos = context.getClickedPos();
            double side = (hit.x - pos.getX() - .5) * right.getStepX()
                    + (hit.z - pos.getZ() - .5) * right.getStepZ();
            return state.setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HINGE, side > 0 ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
        }
    }

    public static final class ForCRH1EndFaceBlock {
        public static BlockState getStateForPlacement(CRH1EndFaceBlock block, BlockPlaceContext context) {
            Direction face = context.getClickedFace();
            return block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, face.getAxis().isHorizontal() ? face : context.getHorizontalDirection());
        }
    }

    public static final class ForCRH1HeadBlock {
        public static BlockState getStateForPlacement(CRH1HeadBlock block, BlockPlaceContext context) {
            Direction face = context.getClickedFace();
            return block.defaultBlockState().setValue(CRH1HeadBlock.FACING, face.getAxis().isHorizontal() ? face : context.getHorizontalDirection());
        }

        public static void setPlacedBy(CRH1HeadBlock block, Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack, Runnable baseSetPlacedBy) {
            baseSetPlacedBy.run();
            if (!level.isClientSide) level.scheduleTick(pos, block, 1);
        }
    }

    public static final class ForCRH1PlainPanelBlock {
        public static BlockState getStateForPlacement(BlockPlaceContext context, Supplier<BlockState> baseGetStateForPlacement) {
            return baseGetStateForPlacement.get().setValue(BlockStateProperties.DOOR_HINGE, SideMirrorBlockBase.getHinge(context));
        }
    }

    public static final class ForCRH2DoorBlock {
        public static BlockState getStateForPlacement(CRH2DoorBlock block, BlockPlaceContext context, Supplier<BlockState> baseGetStateForPlacement) {
            BlockState state = baseGetStateForPlacement.get();
            if (state == null) return null;
            Direction face = context.getClickedFace();
            Direction facing = face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite();
            var hit = context.getClickLocation();
            var pos = context.getClickedPos();
            Direction right = facing.getClockWise();
            double side = (hit.x-pos.getX()-.5)*right.getStepX()+(hit.z-pos.getZ()-.5)*right.getStepZ();
            // The cab source leaf occupies the right of its reference; the passenger source occupies the left.
            boolean mirrored = block.isSlideDoor() ? side > 0 : side < 0;
            return state.setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HINGE, mirrored ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
        }
    }

    public static final class ForCRH2EndFaceBlock {
        public static BlockState getStateForPlacement(CRH2EndFaceBlock block, BlockPlaceContext context) {
            Direction face = context.getClickedFace();
            return block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,
                    face.getAxis().isHorizontal() ? face : context.getHorizontalDirection());
        }
    }

    public static final class ForCRH2HeadBlock {
        public static BlockPlaceContext placementContext(BlockPlaceContext context) { return context; }

        public static BlockState getStateForPlacement(CRH2HeadBlock block, BlockPlaceContext c) {
            Direction face = c.getClickedFace();
            Direction facing = face.getAxis().isHorizontal() ? face : c.getHorizontalDirection();
            return block.defaultBlockState().setValue(CRH2HeadBlock.FACING, facing);
        }

        public static void setPlacedBy(CRH2HeadBlock block, Level w,BlockPos p,BlockState s,LivingEntity who,ItemStack stack, Runnable baseSetPlacedBy) {
            baseSetPlacedBy.run();
            if (!w.isClientSide) {
                HeadBogey.placed(w,p);
                w.scheduleTick(p,block,1);
            }
        }

        public static void placeBogey(Level w,BlockPos root,Direction facing) {
            HeadBogey.refresh(w,root);
        }
    }

    public static final class ForCRH2PanelBlock {
        public static BlockState getStateForPlacement(BlockPlaceContext context, Supplier<BlockState> baseGetStateForPlacement) {
            return baseGetStateForPlacement.get().setValue(CRH2PanelBlock.HINGE,SideMirrorBlockBase.getHinge(context));
        }
    }

    public static final class ForCRH2PartBlock {
        public static AABB placementBounds(CRH2PartBlock block, BlockState state) { return block.geometry().bounds(state.getValue(HorizontalBlockBase.FACING),CRH2PartBlock.right(state)); }

        public static boolean canFitSide(CRH2PartBlock block, Level level, BlockPos pos, BlockState state) {
            return PartInstallation.available(level,block.partCells(level,pos,state),at->false,at->true);
        }

        public static boolean fillSide(CRH2PartBlock block, Level level,BlockPos root,BlockState state) {
            return PartInstallation.install(level,block.partCells(level,root,state),at->false,at->true);
        }

        public static void onPlace(CRH2PartBlock block, BlockState state,Level level,BlockPos pos,BlockState previous,boolean moving, Runnable baseOnPlace) {
            baseOnPlace.run();
            if(!level.isClientSide&&(state.getValue(CRH2PartBlock.PART)==0||block.hasRemoteShape())&&!moving)level.scheduleTick(pos,block,1);
        }
    }

    public static final class ForHeadBogey {
        public static void placed(Level level, BlockPos root) {
            HeadBogey.refresh(level, root, false);
        }
    }
}
