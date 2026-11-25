package willow.train.hexie.compat.mixins.client.render;

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
import willow.train.hexie.bridge.world.BlockEntityViews.CustomRenderedDoorEntityView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH1DoorBlockView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2DoorBlockView;
import willow.train.hexie.compat.create.interaction.CRH1DoorMovement;
import willow.train.hexie.compat.create.interaction.CRH2DoorMovement;

@Mixin(value = ClientContraption.class, remap = false)
public abstract class MixinClientContraptionDoor {
    @Shadow @Final private Contraption contraption;

    @Inject(method = "readBlockEntity", at = @At("RETURN"), require = 1)
    private void hexie$initialiseDoorAnimation(CallbackInfoReturnable<BlockEntity> cir) {
        if (cir.getReturnValue() instanceof CustomRenderedDoorEntityView display
                && display.asBlockEntity().getBlockState().getBlock() instanceof CRH1DoorBlockView
                && display.asBlockEntity().getBlockState().getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER)
            CRH1DoorMovement.initialiseDisplay(contraption, display.asBlockEntity().getBlockPos(), display);
        if (cir.getReturnValue() instanceof CustomRenderedDoorEntityView display
                && display.asBlockEntity().getBlockState().getBlock() instanceof CRH2DoorBlockView
                && display.asBlockEntity().getBlockState().getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER)
            CRH2DoorMovement.initialiseDisplay(contraption, display.asBlockEntity().getBlockPos(), display);
    }
}
