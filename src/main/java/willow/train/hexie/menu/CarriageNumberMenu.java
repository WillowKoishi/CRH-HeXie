package willow.train.hexie.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import willow.train.hexie.api.display.PassengerCarriageNumber;
import willow.train.hexie.api.display.PassengerDisplayHost;
import willow.train.hexie.blocks.parts.PartStructures;
import willow.train.hexie.blocks.signs.PanelDisplayMount;
import willow.train.hexie.blocks.signs.PassengerDisplayPanel;
import willow.train.hexie.init.client.MenuInit;

/** The server retains one exact world or moving-panel target for the lifetime of the editor. */
public final class CarriageNumberMenu extends AbstractContainerMenu {
    public interface Target {
        String number();
        boolean stillValid(Player player);
        void setNumber(String number);
    }
    public final String initialNumber;
    private final Target target;
    public CarriageNumberMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        super(MenuInit.CARRIAGE_NUMBER_MENU.get(), id);
        initialNumber = PassengerCarriageNumber.clean(buf.readUtf(PassengerCarriageNumber.MAX_DIGITS));
        target = null;
    }
    public CarriageNumberMenu(int id, Target target) {
        super(MenuInit.CARRIAGE_NUMBER_MENU.get(), id);
        this.target = target;
        initialNumber = PassengerCarriageNumber.clean(target.number());
    }
    public static boolean open(ServerPlayer player, Target target) {
        if (!target.stillValid(player)) return false;
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new CarriageNumberMenu(id, target),
                Component.translatable("gui.hexie.carriage_number.title")),
                buf -> buf.writeUtf(PassengerCarriageNumber.clean(target.number()), PassengerCarriageNumber.MAX_DIGITS));
        return true;
    }
    public static boolean openStatic(ServerPlayer player, BlockEntity blockEntity) {
        return blockEntity instanceof PassengerDisplayHost && open(player, worldTarget(blockEntity));
    }
    public static Target worldTarget(BlockEntity blockEntity) {
        var host = (PassengerDisplayHost) blockEntity;
        var position = blockEntity.getBlockPos();
        var state = blockEntity.getBlockState();
        return new Target() {
            @Override public String number() { return host.carriageNumber(); }
            @Override public boolean stillValid(Player player) {
                var level = player.level();
                if (!player.mayBuild() || player.isSpectator() || blockEntity.isRemoved()
                        || blockEntity.getLevel() != level || !level.hasChunkAt(position)
                        || level.getBlockEntity(position) != blockEntity || !level.getBlockState(position).equals(state)
                        || !PassengerDisplayPanel.supports(state.getBlock())
                        || !state.hasProperty(PanelDisplayMount.PROPERTY) || !state.getValue(PanelDisplayMount.PROPERTY).hasNumber()
                        || !level.mayInteract(player, position)) return false;
                var root = PartStructures.resolve(level, position);
                if (root == null || !root.pos().equals(position)) return false;
                boolean reachable = false;
                for (var cell : root.structure().partCells(level, position, state).keySet()) {
                    if (!level.hasChunkAt(cell) || !level.mayInteract(player, cell)) return false;
                    reachable |= player.canInteractWithBlock(cell, 1);
                }
                return reachable;
            }
            @Override public void setNumber(String number) { host.setCarriageNumber(number); }
        };
    }
    @Override public boolean stillValid(Player player) {
        return target != null && target.stillValid(player) && target.number().equals(initialNumber);
    }
    public boolean submit(Player player, String number) {
        if (!PassengerCarriageNumber.valid(number) || !stillValid(player)) return false;
        target.setNumber(number);
        return true;
    }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
