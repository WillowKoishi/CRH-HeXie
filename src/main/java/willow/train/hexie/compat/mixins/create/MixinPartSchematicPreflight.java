package willow.train.hexie.compat.mixins.create;

import com.simibubi.create.content.schematics.SchematicPrinter;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.item.ItemStack;
import java.util.Set;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.compat.create.assembly.PartSchematicPlacement;

/** Reject an obstructed whole part before the cannon/deployer consumes its item. */
@Mixin(value=SchematicPrinter.class,remap=false)
public abstract class MixinPartSchematicPreflight {
    @Shadow private SchematicLevel blockReader;
    @Unique private Set<BlockPos> hexie$partMembers=Set.of();
    @Inject(method="loadSchematic",at=@At("RETURN"))
    private void hexie$readPartLayout(ItemStack schematic,Level level,boolean processNBT,CallbackInfo ci) {
        HexieBridges.carriageBlock().headBogeyMigration().schematic(blockReader);
        hexie$partMembers=PartSchematicPlacement.targetMembers(blockReader);
    }
    @Inject(method="shouldPlaceBlock",at=@At("RETURN"),cancellable=true)
    private void hexie$checkPart(Level level,SchematicPrinter.PlacementPredicate predicate,BlockPos pos,CallbackInfoReturnable<Boolean> cir) {
        if(!cir.getReturnValueZ())return;
        var target=blockReader.getBlockState(pos);
        if(target.isAir()&&hexie$partMembers.contains(pos)
                ||!PartSchematicPlacement.canPlace(level,pos,target))cir.setReturnValue(false);
    }
}
