package willow.train.hexie.ui.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.interaction.ToolOpsBridge.ColoredBrushView;
import willow.train.hexie.bridge.ui.MenuBridge.BlockEntityEditMenuTargetView;
import willow.train.hexie.bridge.world.BlockEntityViews.CRH2HeadEntityView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2HeadBlockView;
import willow.train.hexie.bridge.world.WorldBlockBridge.CRH2HeadEntityOperations;

public final class HeadNumberMenu extends BlockEntityEditMenu<CRH2HeadEntityView> {
    public final BlockPos pos;
    public final String initialNumber;
    public HeadNumberMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        super(MenuInit.HEAD_NUMBER_MENU.get(), id, null, CRH2HeadEntityView.class);
        pos = buf.readBlockPos(); initialNumber = buf.readUtf(CRH2HeadEntityOperations.MAX_LENGTH * 2);
        contentOnly = buf.isReadable() && buf.readBoolean(); target = null;
    }
    public HeadNumberMenu(int id, Inventory inventory, CRH2HeadEntityView target) {
        super(MenuInit.HEAD_NUMBER_MENU.get(), id, target.asBlockEntity(), CRH2HeadEntityView.class);
        pos = target.asBlockEntity().getBlockPos(); initialNumber = target.number();
    }
    public static boolean openMoving(net.minecraft.server.level.ServerPlayer player, BlockEntityEditMenuTargetView moving) {
        if (!(moving.entity() instanceof CRH2HeadEntityView head) || !moving.stillValid(player)) return false;
        player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, p) -> {
            var menu = new HeadNumberMenu(id, inv, head);
            menu.attachTarget(moving);
            return menu;
        }, net.minecraft.network.chat.Component.empty()), buf -> {
            buf.writeBlockPos(head.asBlockEntity().getBlockPos());
            buf.writeUtf(head.number(), CRH2HeadEntityOperations.MAX_LENGTH * 2);
            buf.writeBoolean(true);
        });
        return true;
    }
    @Override public boolean stillValid(Player player) {
        if (contentOnly()) return super.stillValid(player);
        if (!player.mayBuild() || player.isSpectator() || !player.level().hasChunkAt(pos)
                || !(player.getMainHandItem().getItem() instanceof ColoredBrushView || player.getOffhandItem().getItem() instanceof ColoredBrushView)
                || !(player.level().getBlockEntity(pos) instanceof CRH2HeadEntityView head)
                || !(head.asBlockEntity().getBlockState().getBlock() instanceof CRH2HeadBlockView)
                || target != null && (target != head || target.asBlockEntity().isRemoved())) return false;
        // The root sits at the rear of the nine-block shell; measure reach to the shell.
        var bounds = HexieBridges.carriageBlock().headGeometry().bounds(head.asBlockEntity().getBlockState().getValue(HexieBridges.carriageBlock().crh2HeadBlock().facing())).move(pos);
        var eye = player.getEyePosition();
        double dx = Math.max(bounds.minX - eye.x, Math.max(0, eye.x - bounds.maxX));
        double dy = Math.max(bounds.minY - eye.y, Math.max(0, eye.y - bounds.maxY));
        double dz = Math.max(bounds.minZ - eye.z, Math.max(0, eye.z - bounds.maxZ));
        return dx * dx + dy * dy + dz * dz <= 64;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
