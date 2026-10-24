package willow.train.hexie.client.util;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.schematics.SchematicItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

public class SchematicItemUtil {
    public static ItemStack create(Level level, ItemStack inputStack, String schematic, String owner) {
        ItemStack blueprint = inputStack.copy();
        blueprint.set(AllDataComponents.SCHEMATIC_DEPLOYED, false);
        blueprint.set(AllDataComponents.SCHEMATIC_OWNER, owner);
        blueprint.set(AllDataComponents.SCHEMATIC_FILE, schematic);
        blueprint.set(AllDataComponents.SCHEMATIC_ANCHOR, BlockPos.ZERO);
        blueprint.set(AllDataComponents.SCHEMATIC_ROTATION, Rotation.NONE);
        blueprint.set(AllDataComponents.SCHEMATIC_MIRROR, Mirror.NONE);
        SchematicItem.writeSize(level, blueprint);
        return blueprint;
    }
}
