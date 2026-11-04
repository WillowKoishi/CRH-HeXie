package willow.train.hexie.client.render.track;

import com.simibubi.create.content.trains.track.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.*;
import java.util.*;
import willow.train.hexie.blocks.track.standard.*;

public final class BallastTrackModel extends BakedModelWrapper<BakedModel> {
    public static BakedModel create(BakedModel original) { return new TrackModel(new BallastTrackModel(original)); }
    public static final ModelProperty<BlockState> TEXTURE=new ModelProperty<>();
    public static final ModelProperty<List<BakedQuad>> FLEXI_BALLAST = new ModelProperty<>();
    public BallastTrackModel(BakedModel original) { super(original); }
    @Override public ModelData getModelData(BlockAndTintGetter world,BlockPos pos,BlockState state,ModelData data) {
        ModelData inherited=super.getModelData(world,pos,state,data);
        if (SlabTrackModels.applies(state))
            return inherited.derive().with(SlabTrackModels.POSITION, pos.immutable()).build();
        return inherited.derive().with(TEXTURE,world.getBlockEntity(pos) instanceof BallastData ballast?
                ballast.hexie$getBallast(null):Blocks.COBBLESTONE.defaultBlockState()).build();
    }
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer) {
        var original=super.getQuads(state,side,random,data,layer);
        if(state==null||side!=null||layer!=null&&layer!=RenderType.cutoutMipped())return original;
        if (SlabTrackModels.applies(state)) {
            List<BakedQuad> result = new ArrayList<>(original);
            BlockPos pos = data.get(SlabTrackModels.POSITION);
            SlabTrackModels.addStraight(result, state.getValue(TrackBlock.SHAPE),
                    pos == null ? BlockPos.ZERO : pos, data.get(SlabTrackModels.FLEXI_PHASE));
            return result;
        }
        if (!TrackBallast.enabled(state)) return original;
        List<BakedQuad> result=new ArrayList<>(original);
        var texture=data.get(TEXTURE);
        var projected = data.get(FLEXI_BALLAST);
        result.addAll(projected != null ? projected : BallastModels.straight(state.getValue(TrackBlock.SHAPE),
                texture == null ? Blocks.COBBLESTONE.defaultBlockState() : texture));
        return result;
    }
}
