package willow.train.hexie.interaction.item.editable_panels;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.PartBlockBridge.VehicleMarkingSurfaceView;
import willow.train.hexie.bridge.world.PartBlockBridge.VehicleMarkingsView;

public final class CRHLogoItem extends Item {
    public CRHLogoItem(Properties properties) { super(properties); }
    @Override public InteractionResult useOn(UseOnContext context) {
        var level=context.getLevel(); var player=context.getPlayer();
        var state=level.getBlockState(context.getClickedPos());
        var resolved=HexieBridges.partBlock().partStructures().resolve(level,context.getClickedPos());
        if(resolved!=null)state=resolved.state();
        if (!(state.getBlock() instanceof VehicleMarkingSurfaceView panel) || player==null) return InteractionResult.PASS;
        if (level!=player.level() || !player.mayBuild() || player.isSpectator()) return InteractionResult.FAIL;
        var root=resolved!=null?resolved.pos():panel.markingRoot(level,context.getClickedPos(),state);
        if (!(level.getBlockEntity(root) instanceof VehicleMarkingsView entity)) return InteractionResult.FAIL;
        int slot=HexieBridges.carriageBlock().harmonyMarking().slot(panel,state,root,context.getClickLocation());
        if (entity.harmony(slot)!=0) return InteractionResult.PASS;
        if (!level.isClientSide) {
            entity.attachHarmony(slot);
            if (!player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
