package willow.train.hexie.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import willow.train.hexie.blockentity.GroundStationSignEntity;
import willow.train.hexie.init.client.MenuInit;

public class GroundStationSignMenu extends AbstractContainerMenu {
    public final BlockPos pos;
    public final String[] initialFields;
    public GroundStationSignMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos(), new String[]{buf.readUtf(128), buf.readUtf(128), buf.readUtf(128), buf.readUtf(128)});
    }
    public GroundStationSignMenu(int id, Inventory inventory, BlockPos pos, String[] fields) {
        super(MenuInit.GROUND_STATION_SIGN_MENU.get(), id); this.pos = pos.immutable(); this.initialFields = fields.clone();
    }
    @Override public boolean stillValid(Player player) {
        return player.mayBuild() && !player.isSpectator() && player.level().hasChunkAt(pos)
                && player.level().getBlockEntity(pos) instanceof GroundStationSignEntity
                && player.distanceToSqr(pos.getX() + .5, pos.getY() + 1.5, pos.getZ() + .5) <= 64;
    }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (id != 0 || player.level().isClientSide || !stillValid(player)) return false;
        if (player.level().getBlockEntity(pos) instanceof GroundStationSignEntity sign) {
            sign.cycleFont();
            return true;
        }
        return false;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
