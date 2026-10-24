package willow.train.hexie.mixins.mixin;

import com.simibubi.create.content.schematics.SchematicItem;
import com.simibubi.create.content.schematics.packet.SchematicSyncPacket;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SchematicSyncPacket.class)
public class MixinSchematicSyncPacket {

    @WrapOperation(method = "handle",
            at = @At(value = "INVOKE", target = "Lcom/tterrag/registrate/util/entry/ItemEntry;isIn(Lnet/minecraft/world/item/ItemStack;)Z"),
            remap = false)
    public boolean hexie$includeCustomSchematics(ItemEntry<?> instance, ItemStack itemStack,
                                                 Operation<Boolean> original) {
        if (instance == com.simibubi.create.AllItems.SCHEMATIC)
            return itemStack.getItem() instanceof SchematicItem;
        return original.call(instance, itemStack);
    }
}
