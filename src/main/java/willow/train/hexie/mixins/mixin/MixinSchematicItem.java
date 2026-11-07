package willow.train.hexie.mixins.mixin;

import com.simibubi.create.content.schematics.SchematicItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import willow.train.hexie.compat.create.PrefabSchematic;

@Mixin(SchematicItem.class)
public class MixinSchematicItem {
    @Inject(method = "loadSchematic", at = @At("HEAD"), cancellable = true, remap = false)
    private static void hexie$loadPrefab(Level level, ItemStack blueprint,
                                         CallbackInfoReturnable<StructureTemplate> cir) {
        StructureTemplate template = PrefabSchematic.load(level, blueprint);
        if (template != null)
            cir.setReturnValue(template);
    }
}
