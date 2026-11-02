package willow.train.hexie.blocks.locos.head;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.*;
import com.simibubi.create.content.trains.track.ITrackBlock;
import willow.train.hexie.blocks.bogeys.*;
import willow.train.hexie.init.block.BlockInit;
import willow.train.hexie.init.track.TrackBlockInit;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = willow.train.hexie.Main.MOD_ID)
public final class CRH2HeadBlock extends Block {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    private static final Map<ServerLevel, Map<BlockPos, Direction>> MOVED_HEADS = new WeakHashMap<>();
    public CRH2HeadBlock(Properties properties) {
        super(properties.dynamicShape());
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) {
        Direction face = c.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : c.getHorizontalDirection();
        return defaultBlockState().setValue(FACING, facing);
    }
    @Override public BlockState rotate(BlockState s,Rotation r) { return s.setValue(FACING,r.rotate(s.getValue(FACING))); }
    @Override public BlockState mirror(BlockState s,Mirror m) { return rotate(s,m.getRotation(s.getValue(FACING))); }
    @Override public RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    @Override public void onBlockStateChange(LevelReader world, BlockPos pos, BlockState oldState, BlockState newState) {
        if (world instanceof Level level && level.isClientSide)
            willow.train.hexie.client.render.CRH2StaticSections.changed(world, pos, oldState, newState);
    }
    @Override public VoxelShape getShape(BlockState s,BlockGetter w,BlockPos p,CollisionContext c) { return HeadGeometry.floor(HeadGeometry.ROOT_PART,s.getValue(FACING)); }
    @Override public VoxelShape getCollisionShape(BlockState s,BlockGetter w,BlockPos p,CollisionContext c) { return getShape(s,w,p,c); }
    @Override public List<ItemStack> getDrops(BlockState s,LootParams.Builder b) { return List.of(asItem().getDefaultInstance()); }
    @Override public void setPlacedBy(Level w,BlockPos p,BlockState s,LivingEntity who,ItemStack stack) {
        super.setPlacedBy(w,p,s,who,stack);
        if (!w.isClientSide) w.scheduleTick(p,this,1);
    }
    @Override protected void neighborChanged(BlockState s,Level w,BlockPos p,Block block,BlockPos from,boolean moving) {
        if (!w.isClientSide) w.scheduleTick(p,this,1);
    }
    @Override protected void tick(BlockState s,ServerLevel w,BlockPos p,RandomSource random) {
        var facing=s.getValue(FACING);
        for(int i=0;i<HeadGeometry.PARTS;i++) {
            var at=p.offset(HeadGeometry.offset(i,facing));
            if (!w.hasChunkAt(at)) return;
            if (!p.equals(HeadGeometry.owner(w,at))) { w.destroyBlock(p,true);return; }
        }
        placeBogey(w,p,facing);
    }
    public static void placeBogey(Level w,BlockPos root,Direction facing) {
        if (w.isClientSide) return;
        var pos=root.offset(HeadGeometry.offset(HeadGeometry.BOGEY_PART,facing));
        if (!(w.getBlockState(pos).getBlock() instanceof HeadFloorBlock)) return;
        var rail=w.getBlockState(pos.below());
        if (!(rail.getBlock() instanceof ITrackBlock track)) return;
        var normal=track.getUpNormal(w,pos.below(),rail);
        var axes=track.getTrackAxes(w,pos.below(),rail);
        if (Math.abs(normal.y-1)>1e-6 || axes.size()!=1) return;
        var axis=axes.getFirst();
        if (Math.abs(axis.y)>1e-6 || (facing.getAxis()==Direction.Axis.X ? Math.abs(axis.z)>1e-6 : Math.abs(axis.x)>1e-6)) return;
        var state=TrackBlockInit.DT206_BOGEY.getDefaultState()
                .setValue(BlockStateProperties.HORIZONTAL_AXIS,facing.getAxis()).setValue(HexieBogeyBlock.HEAD_MOUNTED,true);
        if (!w.setBlock(pos,state,Block.UPDATE_ALL)) return;
        if (w.getBlockEntity(pos) instanceof HexieBogeyBlockEntity bogey)
            BogeyFloorData.setLongitudinal(bogey,3*facing.getAxisDirection().getStep());
    }
    @Override protected void onRemove(BlockState s,Level w,BlockPos p,BlockState replacement,boolean moved) {
        if (!w.isClientSide && !s.is(replacement.getBlock())) {
            var facing=s.getValue(FACING);
            if (moved && w instanceof ServerLevel server)
                MOVED_HEADS.computeIfAbsent(server, ignored -> new HashMap<>()).put(p.immutable(), facing);
            else if (!moved)
                removeRemainingParts(w,p,facing);
        }
        super.onRemove(s,w,p,replacement,moved);
    }

    @SubscribeEvent
    public static void cleanMovedHeadRemainders(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel world)) return;
        var pending = MOVED_HEADS.remove(world);
        if (pending == null) return;
        // Assembly removes the complete snapshot synchronously. Wait until it has
        // finished so source cleanup cannot delete blocks still being captured.
        pending.forEach((root, facing) -> {
            // A rollback or reinstallation owns its parts again.
            if (!(world.getBlockState(root).getBlock() instanceof CRH2HeadBlock))
                removeRemainingParts(world,root,facing);
        });
    }

    private static void removeRemainingParts(Level w,BlockPos root,Direction facing) {
        for(int i=0;i<HeadGeometry.PARTS;i++) {
            if(i==HeadGeometry.ROOT_PART) continue;
            var at=root.offset(HeadGeometry.offset(i,facing));var part=w.getBlockState(at);
            boolean floor=part.getBlock() instanceof HeadFloorBlock && part.getValue(HeadFloorBlock.PART)==i && part.getValue(FACING)==facing;
            boolean support=i==HeadGeometry.BOGEY_PART && part.getBlock() instanceof HexieBogeyBlock<?> && part.getValue(HexieBogeyBlock.HEAD_MOUNTED);
            if(floor||support) w.removeBlock(at,false);
        }
    }
}
