package willow.train.hexie.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import willow.train.hexie.init.client.MenuInit;
import willow.train.hexie.item.track.StandardTrackPavingPreset;

/** Holds one non-consuming ghost slot for the standard track's paving material. */
public final class StandardTrackPavingMenu extends AbstractContainerMenu {
    private final Inventory inventory;
    private final InteractionHand hand;
    private final ItemStack trackStack;
    private final int heldTrackSlot;
    private final SimpleContainer materialSlot = new SimpleContainer(1);

    public StandardTrackPavingMenu(int id, Inventory inventory, FriendlyByteBuf extraData) {
        this(id, inventory, extraData.readEnum(InteractionHand.class));
    }

    public StandardTrackPavingMenu(int id, Inventory inventory, InteractionHand hand) {
        super(MenuInit.STANDARD_TRACK_PAVING_MENU.get(), id);
        this.inventory = inventory;
        this.hand = hand;
        this.trackStack = inventory.player.getItemInHand(hand);
        this.heldTrackSlot = hand == InteractionHand.MAIN_HAND ? 28 + inventory.selected : -1;

        materialSlot.setItem(0, StandardTrackPavingPreset.getBlockStack(trackStack));
        addSlot(new PavingMaterialSlot(materialSlot, 0, 80, 30));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int index = column + (row + 1) * 9;
                addSlot(new Slot(inventory, index, 8 + column * 18, 84 + row * 18));
            }
        }

        for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, column, 8 + column * 18, 142));
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().isClientSide || player.isAlive() && !player.isSpectator()
                && player.getItemInHand(hand) == trackStack
                && StandardTrackPavingPreset.isStandardTrack(trackStack);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return slot.index != 0 && slot.index != heldTrackSlot;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.index != 0 && slot.index != heldTrackSlot
                && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        if (slotId == 0) {
            if (clickType == ClickType.PICKUP) {
                ItemStack selectedBlock = getCarried();
                if (!selectedBlock.isEmpty() && !StandardTrackPavingPreset.isEligibleItem(selectedBlock)) {
                    if (!player.level().isClientSide)
                        player.displayClientMessage(Component.translatable(
                                "gui.hexie.standard_track_paving.invalid_block"), true);
                    return;
                }
                setMaterial(selectedBlock);
            }
            return;
        }

        if (slotId == heldTrackSlot || clickType == ClickType.SWAP
                && (hand == InteractionHand.MAIN_HAND && dragType == inventory.selected
                    || hand == InteractionHand.OFF_HAND && dragType == Inventory.SLOT_OFFHAND))
            return;
        super.clicked(slotId, dragType, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (slotIndex <= 0 || slotIndex >= slots.size() || slotIndex == heldTrackSlot)
            return ItemStack.EMPTY;

        ItemStack selectedBlock = slots.get(slotIndex).getItem();
        if (!StandardTrackPavingPreset.isEligibleItem(selectedBlock)) {
            if (!player.level().isClientSide)
                player.displayClientMessage(Component.translatable(
                        "gui.hexie.standard_track_paving.invalid_block"), true);
            return ItemStack.EMPTY;
        }

        setMaterial(selectedBlock);
        return ItemStack.EMPTY;
    }

    private void setMaterial(ItemStack selectedBlock) {
        StandardTrackPavingPreset.set(trackStack, selectedBlock);
        materialSlot.setItem(0, StandardTrackPavingPreset.getBlockStack(trackStack));
        inventory.setChanged();
        broadcastChanges();
    }

    private final class PavingMaterialSlot extends Slot {
        private PavingMaterialSlot(SimpleContainer container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
