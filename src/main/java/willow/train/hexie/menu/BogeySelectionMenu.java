package willow.train.hexie.menu;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import willow.train.hexie.blocks.bogeys.HexieBogeyBlock;
import willow.train.hexie.blocks.bogeys.HexieBogeyBlockEntity;
import willow.train.hexie.bogey.BogeySelectionCatalog;
import willow.train.hexie.init.client.MenuInit;

/** Server-owned target and choices for the wrench's top-face editor. */
public final class BogeySelectionMenu extends AbstractContainerMenu {
    private final List<BogeySelectionCatalog.Entry> entries = BogeySelectionCatalog.entries();
    private final BlockPos pos;
    private final InteractionHand hand;
    private final HexieBogeyBlockEntity target;
    private final int initialSelection;
    private boolean submitted;

    public BogeySelectionMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        super(MenuInit.BOGEY_SELECTION_MENU.get(), id);
        pos = buf.readBlockPos();
        hand = buf.readEnum(InteractionHand.class);
        initialSelection = buf.readVarInt();
        target = null;
    }

    public BogeySelectionMenu(int id, HexieBogeyBlockEntity target, InteractionHand hand) {
        super(MenuInit.BOGEY_SELECTION_MENU.get(), id);
        this.target = target;
        this.pos = target.getBlockPos().immutable();
        this.hand = hand;
        initialSelection = findSelection(entries, target);
    }

    private static int findSelection(List<BogeySelectionCatalog.Entry> entries, HexieBogeyBlockEntity target) {
        var block = (HexieBogeyBlock<?>) target.getBlockState().getBlock();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            if (entry.style() == target.getStyle() && entry.size().equals(block.getSize())) return i;
        }
        return -1;
    }

    public static boolean open(ServerPlayer player, HexieBogeyBlockEntity target, InteractionHand hand) {
        if (!canEdit(player, target, hand)) return false;
        int selected = findSelection(BogeySelectionCatalog.entries(), target);
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new BogeySelectionMenu(id, target, hand),
                Component.translatable("gui.hexie.bogey_selection.title")), buf -> {
            buf.writeBlockPos(target.getBlockPos());
            buf.writeEnum(hand);
            buf.writeVarInt(selected);
        });
        return true;
    }

    public List<BogeySelectionCatalog.Entry> entries() { return entries; }
    public int initialSelection() { return initialSelection; }

    private static boolean canEdit(Player player, HexieBogeyBlockEntity target, InteractionHand hand) {
        return target != null && !target.isRemoved() && !target.isCarriageView()
                && target.getLevel() == player.level() && !player.isSpectator() && player.mayBuild()
                && player.getItemInHand(hand).getItem() instanceof WrenchItem
                && player.level().hasChunkAt(target.getBlockPos())
                && player.level().getBlockEntity(target.getBlockPos()) == target
                && target.getBlockState().getBlock() instanceof HexieBogeyBlock<?>
                && !target.getBlockState().getValue(HexieBogeyBlock.HEAD_MOUNTED)
                && player.level().mayInteract(player, target.getBlockPos())
                && player.canInteractWithBlock(target.getBlockPos(), 1);
    }

    @Override public boolean stillValid(Player player) {
        return player.level().isClientSide || !submitted && canEdit(player, target, hand);
    }

    @Override public boolean clickMenuButton(Player player, int index) {
        if (player.level().isClientSide || !stillValid(player) || index < 0 || index >= entries.size()) return false;
        var entry = entries.get(index);
        var oldState = target.getBlockState();
        var oldBlock = (HexieBogeyBlock<?>) oldState.getBlock();
        var newBlock = entry.block();
        if (oldBlock.getTrackType(target.getStyle()) != newBlock.getTrackType(entry.style())) return false;
        var nextState = newBlock.defaultBlockState();
        for (var property : oldBlock.propertiesToCopy()) nextState = copyProperty(oldState, nextState, property);

        // Existing adjustments and add-on data win over defaults, except for the chosen style itself.
        var data = entry.style().defaultData.copy().merge(target.getBogeyData());
        data.putString(AbstractBogeyBlockEntity.BOGEY_STYLE_KEY, entry.style().id.toString());
        var level = player.level();
        if (!oldState.equals(nextState) && !level.setBlock(pos, nextState, Block.UPDATE_ALL)) return false;
        if (!(level.getBlockEntity(pos) instanceof HexieBogeyBlockEntity updated)) return false;
        updated.setBogeyData(data);
        updated.setChanged();
        updated.sendData();
        IWrenchable.playRotateSound(level, pos);
        submitted = true;
        return true;
    }

    private static <T extends Comparable<T>> BlockState copyProperty(BlockState source, BlockState target, Property<T> property) {
        return source.hasProperty(property) && target.hasProperty(property)
                ? target.setValue(property, source.getValue(property)) : target;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
