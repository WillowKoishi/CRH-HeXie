package willow.train.hexie.mixins.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.schematics.SchematicItem;
import com.simibubi.create.content.schematics.client.SchematicHandler;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SchematicHandler.class)
public class MixinSchematicHandler {

    @WrapOperation(
            method = "findBlueprintInHand",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/tterrag/registrate/util/entry/ItemEntry;isIn(Lnet/minecraft/world/item/ItemStack;)Z"),
            remap = false)
    private boolean hexie$includeCustomSchematics(
            ItemEntry<?> instance, ItemStack itemStack, Operation<Boolean> original) {
        if (instance == AllItems.SCHEMATIC)
            return itemStack.getItem() instanceof SchematicItem;
        return original.call(instance, itemStack);
    }
}
