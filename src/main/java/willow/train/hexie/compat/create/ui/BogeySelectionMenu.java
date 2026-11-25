package willow.train.hexie.compat.create.ui;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
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
import net.neoforged.fml.ModList;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.CreateTrainBridgePort.BogeySelectionCatalogEntryView;
import willow.train.hexie.bridge.compat.CreateTrainBridgePort.CreateBogeyBlockView;
import willow.train.hexie.bridge.world.BlockEntityViews.HexieBogeyBlockEntityView;

/** Server-owned target and choices for the wrench's top-face editor. */
public final class BogeySelectionMenu extends AbstractContainerMenu {
    private final List<? extends BogeySelectionCatalogEntryView> entries = HexieBridges.createTrain().bogeySelectionCatalog().entries();
    private final BlockPos pos;
    private final InteractionHand hand;
    private final HexieBogeyBlockEntityView target;
    private final int initialSelection;
    private boolean submitted;

    public BogeySelectionMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        super(CreateMenuInit.BOGEY_SELECTION_MENU.get(), id);
        pos = buf.readBlockPos();
        hand = buf.readEnum(InteractionHand.class);
        initialSelection = buf.readVarInt();
        target = null;
    }

    public BogeySelectionMenu(int id, HexieBogeyBlockEntityView target, InteractionHand hand) {
        super(CreateMenuInit.BOGEY_SELECTION_MENU.get(), id);
        this.target = target;
        this.pos = target.asBlockEntity().getBlockPos().immutable();
        this.hand = hand;
        initialSelection = findSelection(entries, target);
    }

    private static int findSelection(List<? extends BogeySelectionCatalogEntryView> entries, HexieBogeyBlockEntityView target) {
        var bogey = (AbstractBogeyBlockEntity) target.asBlockEntity();
        var block = (AbstractBogeyBlock<?>) bogey.getBlockState().getBlock();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            if (entry.style() == bogey.getStyle() && entry.size().equals(block.getSize())) return i;
        }
        return -1;
    }

    public static boolean open(ServerPlayer player, HexieBogeyBlockEntityView target, InteractionHand hand) {
        if (!canEdit(player, target, hand)) return false;
        int selected = findSelection(HexieBridges.createTrain().bogeySelectionCatalog().entries(), target);
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new BogeySelectionMenu(id, target, hand),
                Component.translatable("gui.hexie.bogey_selection.title")), buf -> {
            buf.writeBlockPos(target.asBlockEntity().getBlockPos());
            buf.writeEnum(hand);
            buf.writeVarInt(selected);
        });
        return true;
    }

    public List<? extends BogeySelectionCatalogEntryView> entries() { return entries; }
    public int initialSelection() { return initialSelection; }

    private static boolean canEdit(Player player, HexieBogeyBlockEntityView target, InteractionHand hand) {
        if (target == null || !ModList.get().isLoaded("create")) return false;
        var entity = target.asBlockEntity();
        return !entity.isRemoved() && !target.isCarriageView()
                && entity.getLevel() == player.level() && !player.isSpectator() && player.mayBuild()
                && player.getItemInHand(hand).getItem() instanceof WrenchItem
                && player.level().hasChunkAt(entity.getBlockPos())
                && player.level().getBlockEntity(entity.getBlockPos()) == entity
                && entity.getBlockState().getBlock() instanceof CreateBogeyBlockView
                && !entity.getBlockState().getValue(HexieBridges.worldBlock().hexieBogeyBlock().headMounted())
                && player.level().mayInteract(player, entity.getBlockPos())
                && player.canInteractWithBlock(entity.getBlockPos(), 1);
    }

    @Override public boolean stillValid(Player player) {
        return player.level().isClientSide || !submitted && canEdit(player, target, hand);
    }

    @Override public boolean clickMenuButton(Player player, int index) {
        if (player.level().isClientSide || !stillValid(player) || index < 0 || index >= entries.size()) return false;
        var entry = entries.get(index);
        var current = (AbstractBogeyBlockEntity) target.asBlockEntity();
        var oldState = current.getBlockState();
        var oldBlock = (CreateBogeyBlockView) oldState.getBlock();
        var newBlock = entry.block();
        if (oldBlock.getTrackType(current.getStyle()) != newBlock.getTrackType(entry.style())) return false;
        var nextState = newBlock.asBlock().defaultBlockState();
        for (var property : oldBlock.propertiesToCopy()) nextState = copyProperty(oldState, nextState, property);

        // Existing adjustments and add-on data win over defaults, except for the chosen style itself.
        var data = entry.style().defaultData.copy().merge(current.getBogeyData());
        data.putString(AbstractBogeyBlockEntity.BOGEY_STYLE_KEY, entry.style().id.toString());
        var level = player.level();
        if (!oldState.equals(nextState) && !level.setBlock(pos, nextState, Block.UPDATE_ALL)) return false;
        if (!(level.getBlockEntity(pos) instanceof HexieBogeyBlockEntityView updated)) return false;
        var bogey = (AbstractBogeyBlockEntity) updated.asBlockEntity();
        bogey.setBogeyData(data);
        bogey.setChanged();
        bogey.sendData();
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
