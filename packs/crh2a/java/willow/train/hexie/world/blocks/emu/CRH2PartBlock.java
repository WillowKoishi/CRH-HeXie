package willow.train.hexie.world.blocks.emu;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.CarriageBlockBridge;
import willow.train.hexie.core.api.interaction.HexieItemRequirementProvider;
import willow.train.hexie.world.blockinteract.EmuBlockInteract;
import willow.train.hexie.world.blocks.base.HorizontalBlockBase;
import willow.train.hexie.world.blocks.parts.PartStructure;
import willow.train.hexie.world.blocks.signs.PassengerDisplayPanel;
import willow.train.hexie.world.collision.CRH2PartShapes;
import willow.train.hexie.world.collision.EmuCollision;
import willow.train.hexie.world.placement.EmuPlacement;
import willow.train.hexie.world.placement.PartInstallation;

/** Ordinary single block, with an exterior side slab like the existing carriage panels. */
public class CRH2PartBlock extends HorizontalBlockBase implements PartStructure,
        HexieItemRequirementProvider, CarriageBlockBridge.CRH2PartBlockView {

    private final CRH2PartShapes geometry;
    private final boolean floor;
    public CRH2PartBlock(BlockBehaviour.Properties properties, String model) {
        this(properties, model, model.equals("crh2a_floor"));
    }
    protected CRH2PartBlock(BlockBehaviour.Properties properties, String model, boolean floor) {
        super(properties.dynamicShape());
        geometry = new CRH2PartShapes(model);
        this.floor = floor;
    }
    public static final IntegerProperty PART = IntegerProperty.create("part",0,15);
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) {
        super.createBlockStateDefinition(b);b.add(PART);
    }
    public boolean hasRemoteShape() {
        return EmuCollision.ForCRH2PartBlock.hasRemoteShape(this);
    }
    public static boolean right(BlockState s) {
        return s.hasProperty(CRH2PanelBlock.HINGE)&&s.getValue(CRH2PanelBlock.HINGE)==DoorHingeSide.RIGHT;
    }
    public BlockPos offset(int i,BlockState state) {
        var p=geometry.cells().get(i);
        if(right(state))p=new BlockPos(-p.getX(),p.getY(),p.getZ());
        return CRH2PartShapes.rotate(p,state.getValue(FACING));
    }
    public int partCount() { return geometry.cells().size(); }
    public BlockPos offset(int i,Direction facing) { return CRH2PartShapes.rotate(geometry.cells().get(i),facing); }
    public BlockPos root(BlockPos pos,BlockState state) {
        int i=state.getValue(PART);return i<partCount()?pos.subtract(offset(i,state)):pos;
    }
    @Override public BlockPos partRoot(BlockGetter level, BlockPos pos, BlockState state) { return root(pos,state); }
    @Override public Map<BlockPos,BlockState> partCells(BlockGetter level,BlockPos root,BlockState state) {
        var cells=new LinkedHashMap<BlockPos,BlockState>();
        for(int i=0;i<partCount();i++) cells.put(root.offset(offset(i,state)),state.setValue(PART,i));
        return cells;
    }
    @Override public Requirement getRequiredItems(BlockState state, BlockEntity entity) {
        return state.getValue(PART)==0 ? Requirement.of(
                Kind.CONSUME,
                PassengerDisplayPanel.withDisplayDrops(state, List.of(asItem().getDefaultInstance())))
                : Requirement.NONE;
    }
    @Override public RenderShape getRenderShape(BlockState s) {
        return s.getValue(PART)==0?RenderShape.MODEL:RenderShape.INVISIBLE;
    }
    @Override public void onBlockStateChange(LevelReader world, BlockPos pos, BlockState oldState, BlockState newState) {
        super.onBlockStateChange(world,pos,oldState,newState);
        if (world instanceof Level level && level.isClientSide)
            HexieBridges.Client.carriageRender().crh2StaticSections().changed(world, pos, oldState, newState);
    }
    public AABB placementBounds(BlockState state) {
        return EmuPlacement.ForCRH2PartBlock.placementBounds(this, state);
    }
    @Override public VoxelShape getShape(BlockState s, BlockGetter w, BlockPos p, CollisionContext c) {
        return EmuCollision.ForCRH2PartBlock.getShape(this, s, w, p, c);
    }
    @Override public VoxelShape getCollisionShape(BlockState s, BlockGetter w, BlockPos p, CollisionContext c) {
        return EmuCollision.ForCRH2PartBlock.getCollisionShape(this, s, w, p, c);
    }

    /** Check occupied cells, not the full visual bounds or just other collision shapes. */
    public boolean canFitSide(Level level, BlockPos pos, BlockState state) {
        return EmuPlacement.ForCRH2PartBlock.canFitSide(this, level, pos, state);
    }

    public boolean fillSide(Level level,BlockPos root,BlockState state) {
        return EmuPlacement.ForCRH2PartBlock.fillSide(this, level, root, state);
    }
    @Override protected void onRemove(BlockState s,Level level,BlockPos pos,BlockState next,boolean moving) {
        if(!moving&&!s.is(next.getBlock())) PartInstallation.removeOtherCells(
                level,pos,partCells(level,root(pos,s),s.setValue(PART,0)));
        super.onRemove(s,level,pos,next,moving);
    }
    @Override public List<ItemStack> getDrops(BlockState s,LootParams.Builder b) {
        if(s.getValue(PART)==0)return super.getDrops(s,b);
        var origin=b.getOptionalParameter(LootContextParams.ORIGIN);
        if(origin==null)return List.of();
        var at=root(BlockPos.containing(origin),s);
        var owner=b.getLevel().getBlockState(at);
        return owner.is(this)&&owner.getValue(PART)==0 ? super.getDrops(owner,b) : List.of();
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState s,Player player) {
        if(!level.isClientSide&&s.getValue(PART)!=0)level.destroyBlock(root(pos,s),!player.isCreative(),player);
        return super.playerWillDestroy(level,pos,s,player);
    }

    @Override public InteractionResult onWrenched(BlockState s,UseOnContext c) {
        return EmuBlockInteract.ForCRH2PartBlock.onWrenched(this, s, c);
    }
    @Override public InteractionResult onSneakWrenched(BlockState s,UseOnContext c) {
        return EmuBlockInteract.ForCRH2PartBlock.onSneakWrenched(this, s, c, super::onSneakWrenched);
    }

    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState previous,boolean moving) {
        EmuPlacement.ForCRH2PartBlock.onPlace(this, state, level, pos, previous, moving, () -> super.onPlace(state,level,pos,previous,moving));
    }
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        if(hasRemoteShape()&&state.getValue(PART)!=0) {level.removeBlock(pos,false);return;}
        if(state.getValue(PART)==0)fillSide(level,pos,state);
    }
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving) {
        if(!level.isClientSide&&!moving&&!PartInstallation.mutating()) {
            var root=root(pos,state);
            if(level.hasChunkAt(root)&&level.getBlockState(root).is(this))level.scheduleTick(root,this,1);
        }
    }

    public final CRH2PartShapes geometry() { return geometry; }
    public final boolean isFloor() { return floor; }
}
