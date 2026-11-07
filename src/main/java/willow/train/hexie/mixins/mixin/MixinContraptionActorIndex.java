package willow.train.hexie.mixins.mixin;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import java.util.List;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.apache.commons.lang3.tuple.MutablePair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.compat.create.IndexedContraptionActors;

/** Start with an indexed empty list, before Create's normal capture/read loops populate it. */
@Mixin(Contraption.class)
public abstract class MixinContraptionActorIndex {
    @Shadow protected List<MutablePair<StructureBlockInfo, MovementContext>> actors;
    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void hexie$indexActorsAtConstruction(CallbackInfo ci) {
        actors = new IndexedContraptionActors();
    }
}
