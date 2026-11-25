package willow.train.hexie.world.blocks.emu;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.BlockEntityViews.CRH2HeadEntityView;
import willow.train.hexie.bridge.world.CarriageBlockBridge;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.world.blockentity.head.CRH2HeadEntity;
import willow.train.hexie.world.blocks.parts.PartStructure;
import willow.train.hexie.world.collision.EmuCollision;
import willow.train.hexie.world.placement.EmuPlacement;
import willow.train.hexie.world.placement.PartInstallation;
import willow.train.hexie.world.registry.blocks.BlockInit;

@EventBusSubscriber(modid = HexieConstants.MOD_ID)
public class CRH2HeadBlock extends Block implements EntityBlock, PartStructure, CarriageBlockBridge.CRH2HeadBlockView {
    /** Optional backends keep their own moving bogey while sharing the complete head structure. */
    public boolean hasPhysicalBogey() { return false; }
    public boolean requiresTrackForBogey() { return true; }
    public boolean preserveBogeyState(Level level, BlockPos root) { return false; }
    public void bogeyChanged(CRH2HeadEntityView head) {}
    @Override public BlockPos partRoot(BlockGetter level,BlockPos pos,BlockState state) { return pos; }
    @Override public Map<BlockPos,BlockState> partCells(BlockGetter level,BlockPos root,BlockState state) {
        var cells=new LinkedHashMap<BlockPos,BlockState>();
        var facing=state.getValue(FACING);
        cells.put(root,state);
        for(int i=0;i<HeadGeometry.PARTS;i++) {
            if(i==HeadGeometry.ROOT_PART)continue;
            var pos=root.offset(HeadGeometry.offset(i,facing));
            cells.put(pos,BlockInit.CRH2A_HEAD_FLOOR.get().defaultBlockState().setValue(FACING,facing).setValue(HeadFloorBlock.PART,i));
        }
        return cells;
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CRH2HeadEntity(pos, state);
    }
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    private record RemovedHead(BlockState state, Map<BlockPos, BlockState> cells) {}
    private static final Map<ServerLevel, Map<BlockPos, CRH2HeadBlock.RemovedHead>> MOVED_HEADS = new WeakHashMap<>();
    public CRH2HeadBlock(BlockBehaviour.Properties properties) {
        super(properties.dynamicShape());
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING); }
    public BlockPlaceContext placementContext(BlockPlaceContext context) {
        return EmuPlacement.ForCRH2HeadBlock.placementContext(context);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) {
        return EmuPlacement.ForCRH2HeadBlock.getStateForPlacement(this, c);
    }
    @Override public BlockState rotate(BlockState s,Rotation r) { return s.setValue(FACING,r.rotate(s.getValue(FACING))); }
    @Override public BlockState mirror(BlockState s,Mirror m) { return rotate(s,m.getRotation(s.getValue(FACING))); }
    @Override public RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    @Override public void onBlockStateChange(LevelReader world, BlockPos pos, BlockState oldState, BlockState newState) {
        if (world instanceof Level level && level.isClientSide)
            HexieBridges.Client.carriageRender().crh2StaticSections().changed(world, pos, oldState, newState);
        if (world instanceof ServerLevel level && oldState.is(this) && newState.is(this)
                && oldState.getValue(FACING) != newState.getValue(FACING)) {
            if (level.getBlockEntity(pos) instanceof CRH2HeadEntity head)
                head.reorientBogey(oldState.getValue(FACING), newState.getValue(FACING));
            level.scheduleTick(pos, this, 1);
        }
    }
    @Override public VoxelShape getShape(BlockState s,BlockGetter w,BlockPos p,CollisionContext c) {
        return EmuCollision.ForCRH2HeadBlock.getShape(s, w, p, c);
    }
    @Override public VoxelShape getCollisionShape(BlockState s,BlockGetter w,BlockPos p,CollisionContext c) {
        return EmuCollision.ForCRH2HeadBlock.getCollisionShape(this, s, w, p, c);
    }
    @Override public List<ItemStack> getDrops(BlockState s,LootParams.Builder b) { return List.of(asItem().getDefaultInstance()); }
    @Override public void setPlacedBy(Level w,BlockPos p,BlockState s,LivingEntity who,ItemStack stack) {
        EmuPlacement.ForCRH2HeadBlock.setPlacedBy(this, w, p, s, who, stack, () -> super.setPlacedBy(w,p,s,who,stack));
    }
    @Override protected void neighborChanged(BlockState s,Level w,BlockPos p,Block block,BlockPos from,boolean moving) {
        if (!w.isClientSide) w.scheduleTick(p,this,1);
    }
    @Override protected void tick(BlockState s,ServerLevel w,BlockPos p,RandomSource random) {
        var facing=s.getValue(FACING);
        HeadBogey.refresh(w,p);
        for(var cell:partCells(w,p,s).entrySet()) {
            var at=cell.getKey();
            if (!w.hasChunkAt(at)) return;
            if (!ownsCell(w,p,s,at,cell.getValue())) {
                PartInstallation.install(w,partCells(w,p,s),ignored->false,ignored->true,
                        () -> HeadBogey.refresh(w,p));
                return;
            }
        }
        placeBogey(w,p,facing);
    }
    public static void placeBogey(Level w,BlockPos root,Direction facing) {
        EmuPlacement.ForCRH2HeadBlock.placeBogey(w, root, facing);
    }
    @Override protected void onRemove(BlockState s,Level w,BlockPos p,BlockState replacement,boolean moved) {
        if (!w.isClientSide && !s.is(replacement.getBlock()) && !PartInstallation.mutating()) {
            var facing=s.getValue(FACING);
            if (moved && w instanceof ServerLevel server)
                MOVED_HEADS.computeIfAbsent(server, ignored -> new HashMap<>())
                        .put(p.immutable(), new CRH2HeadBlock.RemovedHead(s, partCells(w,p,s)));
            else if (!moved)
                removeRemainingParts(w,p,s,partCells(w,p,s));
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
        pending.forEach((root, removed) -> {
            // A rollback or reinstallation owns its parts again.
            if (!(world.getBlockState(root).getBlock() instanceof CRH2HeadBlock))
                removeRemainingParts(world,root,removed.state(),removed.cells());
        });
    }

    private static void removeRemainingParts(Level w,BlockPos root,BlockState state,Map<BlockPos,BlockState> cells) {
        PartInstallation.removeOtherCells(w,root,cells);
        var facing=state.getValue(FACING);
        for(int i=0;i<HeadGeometry.PARTS;i++) {
            if(i==HeadGeometry.ROOT_PART) continue;
            var at=root.offset(HeadGeometry.offset(i,facing));var part=w.getBlockState(at);
            boolean floor=part.getBlock() instanceof HeadFloorBlock && part.getValue(HeadFloorBlock.PART)==i && part.getValue(FACING)==facing;
            boolean support=i==HeadGeometry.BOGEY_PART && HeadBogey.isHeadMountedBogey(part);
            if(floor||support) w.removeBlock(at,false);
        }
    }

    @Override public Block asBlock() { return this; }
}
