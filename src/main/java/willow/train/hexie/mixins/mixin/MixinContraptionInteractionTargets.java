package willow.train.hexie.mixins.mixin;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.compat.create.ContraptionInteractionTargets;

/** Populate once on attachment/load and maintain only the changed local owner. */
@Mixin(AbstractContraptionEntity.class)
public abstract class MixinContraptionInteractionTargets {
    @Shadow protected Contraption contraption;

    @Inject(method = "setContraption", at = @At("RETURN"), remap = false)
    private void hexie$attachedTargets(Contraption attached, CallbackInfo ci) {
        ContraptionInteractionTargets.ensure(attached);
    }

    @Inject(method = "readAdditional", at = @At("RETURN"), remap = false)
    private void hexie$loadedTargets(CompoundTag data, boolean spawnData, CallbackInfo ci) {
        ContraptionInteractionTargets.ensure(contraption);
    }

    @Inject(method = "setBlock", at = @At("RETURN"), remap = false)
    private void hexie$changedTarget(BlockPos position, StructureBlockInfo requested, CallbackInfo ci) {
        hexie$updateTarget(position);
    }

    // Create removes this client-only method from dedicated-server distributions.
    @Inject(method = "handleBlockChange", at = @At("RETURN"), remap = false, require = 0)
    private void hexie$receivedTarget(BlockPos position, BlockState requested, CallbackInfo ci) {
        hexie$updateTarget(position);
    }

    @org.spongepowered.asm.mixin.Unique
    private void hexie$updateTarget(BlockPos position) {
        if (contraption == null) return;
        var info = contraption.getBlocks().get(position);
        if (info == null) ContraptionInteractionTargets.remove(contraption, position);
        else ContraptionInteractionTargets.update(contraption, position, info.state());
    }
}
