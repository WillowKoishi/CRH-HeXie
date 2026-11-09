package willow.train.hexie.mixins.mixin;

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

/** Reject an obstructed whole part before the cannon/deployer consumes its item. */
@Mixin(value=SchematicPrinter.class,remap=false)
public abstract class MixinPartSchematicPreflight {
    @Shadow private SchematicLevel blockReader;
    @Unique private Set<BlockPos> hexie$partMembers=Set.of();
    @Inject(method="loadSchematic",at=@At("RETURN"))
    private void hexie$readPartLayout(ItemStack schematic,Level level,boolean processNBT,CallbackInfo ci) {
        willow.train.hexie.blocks.emu.head.HeadBogeyMigration.schematic(blockReader);
        hexie$partMembers=willow.train.hexie.compat.create.PartSchematicPlacement.targetMembers(blockReader);
    }
    @Inject(method="shouldPlaceBlock",at=@At("RETURN"),cancellable=true)
    private void hexie$checkPart(Level level,SchematicPrinter.PlacementPredicate predicate,BlockPos pos,CallbackInfoReturnable<Boolean> cir) {
        if(!cir.getReturnValueZ())return;
        var target=blockReader.getBlockState(pos);
        if(target.isAir()&&hexie$partMembers.contains(pos)
                ||!willow.train.hexie.compat.create.PartSchematicPlacement.canPlace(level,pos,target))cir.setReturnValue(false);
    }
}
