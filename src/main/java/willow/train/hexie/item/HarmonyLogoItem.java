package willow.train.hexie.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import willow.train.hexie.blockentity.CRH2PartEntity;
import willow.train.hexie.blocks.locos.crh2a.CRH2PanelBlock;
import willow.train.hexie.blocks.locos.crh2a.HarmonyMarking;

public final class HarmonyLogoItem extends Item {
    public HarmonyLogoItem(Properties properties) { super(properties); }
    @Override public InteractionResult useOn(UseOnContext context) {
        var level=context.getLevel(); var player=context.getPlayer();
        var state=level.getBlockState(context.getClickedPos());
        if (!(state.getBlock() instanceof CRH2PanelBlock panel) || player==null) return InteractionResult.PASS;
        if (level!=player.level() || !player.mayBuild() || player.isSpectator()) return InteractionResult.FAIL;
        var root=panel.root(context.getClickedPos(),state);
        if (!(level.getBlockEntity(root) instanceof CRH2PartEntity entity)) return InteractionResult.FAIL;
        int slot=HarmonyMarking.slot(panel,state,root,context.getClickLocation());
        if (entity.harmony(slot)!=0) return InteractionResult.PASS;
        if (!level.isClientSide) {
            entity.attachHarmony(slot);
            if (!player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
