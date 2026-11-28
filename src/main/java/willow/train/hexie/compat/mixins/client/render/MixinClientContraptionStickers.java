package willow.train.hexie.compat.mixins.client.render;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.render.ClientContraption;
import com.simibubi.create.content.contraptions.render.ClientContraption.RenderedBlocks;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import willow.train.hexie.bridge.sticker.ContraptionRenderFilter;

/**
 * Hides blocks whose geometry is owned and rendered by HeXie's renderer engine
 * (StaticShell carriage shells and window-cut contraption blocks) from Create/Flywheel's static contraption mesh.
 */
@Mixin(value = ClientContraption.class, remap = false)
public abstract class MixinClientContraptionStickers {
    @Shadow @Final private Contraption contraption;

    @Inject(method = "getRenderedBlocks", at = @At("RETURN"), cancellable = true)
    private void hexie$hideManagedBlocks(CallbackInfoReturnable<RenderedBlocks> cir) {
        RenderedBlocks base = cir.getReturnValue();
        if (base == null || contraption == null) return;
        Function<BlockPos, BlockState> lookup = base.lookup();
        cir.setReturnValue(new RenderedBlocks(pos -> {
            BlockState state = lookup.apply(pos);
            if (state != null && !state.isAir() && ContraptionRenderFilter.shouldHide(contraption, pos, state)) {
                return Blocks.AIR.defaultBlockState();
            }
            return state;
        }, base.positions()));
    }
}
