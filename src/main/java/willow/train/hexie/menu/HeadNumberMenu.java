package willow.train.hexie.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import willow.train.hexie.blockentity.CRH2HeadEntity;
import willow.train.hexie.blocks.emu.head.HeadGeometry;
import willow.train.hexie.blocks.emu.head.CRH2HeadBlock;
import willow.train.hexie.init.client.MenuInit;
import willow.train.hexie.item.editable_panels.ColoredBrush;

public final class HeadNumberMenu extends BlockEntityEditMenu<CRH2HeadEntity> {
    public final BlockPos pos;
    public final String initialNumber;
    public HeadNumberMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        super(MenuInit.HEAD_NUMBER_MENU.get(), id, null, CRH2HeadEntity.class);
        pos = buf.readBlockPos(); initialNumber = buf.readUtf(CRH2HeadEntity.MAX_LENGTH * 2); target = null;
    }
    public HeadNumberMenu(int id, Inventory inventory, CRH2HeadEntity target) {
        super(MenuInit.HEAD_NUMBER_MENU.get(), id, target, CRH2HeadEntity.class);
        pos = target.getBlockPos(); initialNumber = target.number();
    }
    @Override public boolean stillValid(Player player) {
        if (!player.mayBuild() || player.isSpectator() || !player.level().hasChunkAt(pos)
                || !(player.getMainHandItem().getItem() instanceof ColoredBrush || player.getOffhandItem().getItem() instanceof ColoredBrush)
                || !(player.level().getBlockEntity(pos) instanceof CRH2HeadEntity head)
                || !(head.getBlockState().getBlock() instanceof CRH2HeadBlock)
                || target != null && (target != head || target.isRemoved())) return false;
        // The root sits at the rear of the nine-block shell; measure reach to the shell.
        var bounds = HeadGeometry.bounds(head.getBlockState().getValue(CRH2HeadBlock.FACING)).move(pos);
        var eye = player.getEyePosition();
        double dx = Math.max(bounds.minX - eye.x, Math.max(0, eye.x - bounds.maxX));
        double dy = Math.max(bounds.minY - eye.y, Math.max(0, eye.y - bounds.maxY));
        double dz = Math.max(bounds.minZ - eye.z, Math.max(0, eye.z - bounds.maxZ));
        return dx * dx + dy * dy + dz * dz <= 64;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
