package willow.train.hexie.compat.create.block;

import com.simibubi.create.api.schematic.requirement.SchematicRequirementRegistries;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.content.schematics.requirement.ItemRequirement.ItemUseType;
import com.simibubi.create.content.schematics.requirement.ItemRequirement.StackRequirement;
import com.simibubi.create.content.schematics.requirement.ItemRequirement.StrictNbtStackRequirement;
import java.util.ArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import willow.train.hexie.core.api.interaction.HexieItemRequirementProvider;
import willow.train.hexie.core.api.interaction.HexieItemRequirementProvider.Requirement;

public final class CreateSchematicRequirements {
    private CreateSchematicRequirements() {}

    /** Register after blocks have been added to the block registry. */
    public static void register() {
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block instanceof HexieItemRequirementProvider provider)
                SchematicRequirementRegistries.BLOCKS.register(block,
                        (state, be) -> toCreate(provider.getRequiredItems(state, be)));
        }
    }

    static ItemRequirement toCreate(Requirement requirement) {
        if (requirement.invalid()) return ItemRequirement.INVALID;
        var stacks = new ArrayList<StackRequirement>();
        for (var entry : requirement.entries()) {
            switch (entry.kind()) {
                case NONE -> {}
                case INVALID -> { return ItemRequirement.INVALID; }
                case CONSUME -> stacks.add(new StackRequirement(entry.stack(), ItemUseType.CONSUME));
                case STRICT_CONSUME -> stacks.add(new StrictNbtStackRequirement(entry.stack(), ItemUseType.CONSUME));
                case USE -> stacks.add(new StackRequirement(entry.stack(), ItemUseType.DAMAGE));
            }
        }
        return stacks.isEmpty() ? ItemRequirement.NONE : new ItemRequirement(stacks);
    }
}
