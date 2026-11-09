package willow.train.hexie.blocks.carriage;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import willow.train.hexie.Main;
import willow.train.hexie.blocks.parts.*;
import willow.train.hexie.blocks.signs.*;
import willow.train.hexie.init.block.BlockInit;
import willow.train.hexie.init.item.EditablePanelItemInit;
import willow.train.hexie.item.editable_panels.ColoredBrush;

/** Resolve first, replace once, and consume the actual used hand only after a successful commit. */
public final class PanelConversion {
    private PanelConversion() {}
    public static InteractionResult bottom(Level level,BlockPos pos,BlockState state,Player player,InteractionHand hand,BlockHitResult hit) {
        var stack=player.getItemInHand(hand);var block=state.getBlock();
        boolean brush=stack.is(EditablePanelItemInit.ColoredBrush.get());
        boolean board=stack.is(EditablePanelItemInit.LACQUERED_BOARD.get());
        if((!brush&&!board)||!Main.PANEL_TYPE_UTIL.canTransformPanel(block))return InteractionResult.PASS;
        var type=Main.PANEL_TYPE_UTIL.matchBottomBlock2Panel(block);
        var target=brush?BlockInit.TRAIN_BOTTOM_PANEL_BLOCK.get().defaultBlockState().setValue(TrainPanelBlock.TYPE,type)
                :BlockInit.LAQUERED_BOARD_BLOCK.get().defaultBlockState().setValue(LacqueredBoardBlock.TYPE,type);
        return apply(level,pos,state,target,player,hand,hit,brush);
    }
    public static InteractionResult upper(Level level,BlockPos pos,BlockState state,Player player,InteractionHand hand,BlockHitResult hit) {
        if(!player.getItemInHand(hand).is(EditablePanelItemInit.Sticker.get())||!Main.PANEL_TYPE_UTIL.canTransformUpperPanel(state.getBlock()))return InteractionResult.PASS;
        var target=BlockInit.TRAIN_NO_BLOCK.get().defaultBlockState().setValue(TrainNoBlock.TYPE,Main.PANEL_TYPE_UTIL.matchUpperBlock2Panel(state.getBlock()));
        return apply(level,pos,state,target,player,hand,hit,false);
    }
    public static InteractionResult slab(Level level,BlockPos pos,BlockState state,Player player,InteractionHand hand,BlockHitResult hit) {
        if(!player.getItemInHand(hand).is(EditablePanelItemInit.ColoredBrush.get())||!Main.PANEL_TYPE_UTIL.canTransformSlab(state.getBlock()))return InteractionResult.PASS;
        var target=BlockInit.TRAIN_SPEED_BLOCK.get().defaultBlockState().setValue(TrainSpeedSignBlock.TYPE,Main.PANEL_TYPE_UTIL.matchSlabBlock2Panel(state.getBlock()));
        return apply(level,pos,state,target,player,hand,hit,true);
    }
    private static <T extends Comparable<T>> BlockState copy(BlockState source,BlockState target,Property<T> property) {
        return target.hasProperty(property)?target.setValue(property,source.getValue(property)):target;
    }
    public static BlockState preservePlacement(BlockState source,BlockState target) {
        for(var property:source.getProperties())if(!property.getName().equals("type"))target=copy(source,target,property);
        return target;
    }
    public static InteractionResult apply(Level level,BlockPos pos,BlockState old,BlockState target,Player player,InteractionHand hand,BlockHitResult hit,boolean brush) {
        if(!player.mayBuild()||player.isSpectator()||!player.mayUseItemAt(pos,hit.getDirection(),player.getItemInHand(hand)))return InteractionResult.FAIL;
        // A different block type would discard the display host and its number. Remove the displays first.
        if(!old.is(target.getBlock())&&old.hasProperty(PanelDisplayMount.PROPERTY)
                &&old.getValue(PanelDisplayMount.PROPERTY)!=PanelDisplayMount.NONE)return InteractionResult.FAIL;
        target=preservePlacement(old,target);
        var previous=old.getBlock() instanceof PartStructure part?part.partCells(level,pos,old):Map.of(pos,old);
        var next=target.getBlock() instanceof PartStructure part?part.partCells(level,pos,target):Map.of(pos,target);
        if(!PartInstallation.replace(level,previous,next,at->false,at->player.mayUseItemAt(at,hit.getDirection(),player.getItemInHand(hand))))return InteractionResult.FAIL;
        if(level.isClientSide) {
            for(int i=0;i<12;i++)level.addParticle(ParticleTypes.HAPPY_VILLAGER,pos.getX()+level.random.nextDouble(),pos.getY()+level.random.nextDouble(),pos.getZ()+level.random.nextDouble(),0,.05,0);
        } else {
            level.playSound(null,pos,SoundEvents.PLAYER_LEVELUP,SoundSource.BLOCKS,8,1);
            if(!player.isCreative()) {
                if(brush)player.setItemInHand(hand,ColoredBrush.onEffectiveUse(player.getItemInHand(hand)));
                else player.getItemInHand(hand).shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
