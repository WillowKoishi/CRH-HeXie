package willow.train.hexie.core.api.interaction;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public interface HexieItemRequirementProvider {
    enum Kind { NONE, INVALID, CONSUME, STRICT_CONSUME, USE }

    record Entry(Kind kind, ItemStack stack) {}

    record Requirement(boolean invalid, List<Entry> entries) {
        public static final Requirement NONE = new Requirement(false, List.of());
        public static final Requirement INVALID = new Requirement(true, List.of());

        public Requirement {
            entries = List.copyOf(entries);
        }

        public static Requirement of(Kind kind, ItemStack stack) {
            return of(kind, List.of(stack));
        }

        public static Requirement of(Kind kind, List<ItemStack> stacks) {
            return switch (kind) {
                case NONE -> NONE;
                case INVALID -> INVALID;
                default -> of(stacks.stream().map(stack -> new Entry(kind, stack)).toList());
            };
        }

        public static Requirement of(List<Entry> entries) {
            return entries.isEmpty() ? NONE : new Requirement(false, entries);
        }
    }

    Requirement getRequiredItems(BlockState state, @Nullable BlockEntity blockEntity);
}
