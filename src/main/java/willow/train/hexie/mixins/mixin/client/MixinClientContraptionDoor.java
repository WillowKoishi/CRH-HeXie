package willow.train.hexie.mixins.mixin.client;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.render.ClientContraption;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import willow.train.hexie.blockentity.CustomRenderedDoorEntity;
import willow.train.hexie.blocks.emu.crh2a.CRH2DoorBlock;
import willow.train.hexie.compat.create.CRH2DoorMovement;

@Mixin(value = ClientContraption.class, remap = false)
public abstract class MixinClientContraptionDoor {
    @Shadow @Final private Contraption contraption;

    @Inject(method = "readBlockEntity", at = @At("RETURN"), require = 1)
    private void hexie$initialiseDoorAnimation(CallbackInfoReturnable<BlockEntity> cir) {
        if (cir.getReturnValue() instanceof CustomRenderedDoorEntity display
                && display.getBlockState().getBlock() instanceof CRH2DoorBlock
                && display.getBlockState().getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER)
            CRH2DoorMovement.initialiseDisplay(contraption, display.getBlockPos(), display);
    }
}
