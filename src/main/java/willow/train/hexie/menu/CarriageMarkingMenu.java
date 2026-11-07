package willow.train.hexie.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import willow.train.hexie.init.client.MenuInit;
import willow.train.hexie.item.editable_panels.ColoredBrush;

public final class CarriageMarkingMenu extends BlockEntityEditMenu<net.minecraft.world.level.block.entity.BlockEntity> {
    public final BlockPos pos;
    public final String[] initialFields;
    public final boolean initialLeft;
    public CarriageMarkingMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        super(MenuInit.CARRIAGE_MARKING_MENU.get(), id, null, net.minecraft.world.level.block.entity.BlockEntity.class);
        pos=buf.readBlockPos(); initialFields=new String[]{buf.readUtf(128),buf.readUtf(128),buf.readUtf(128)};
        initialLeft=buf.readBoolean(); contentOnly=buf.isReadable()&&buf.readBoolean(); target=null;
    }
    public CarriageMarkingMenu(int id, Inventory inventory, net.minecraft.world.level.block.entity.BlockEntity target) {
        super(MenuInit.CARRIAGE_MARKING_MENU.get(), id, target, net.minecraft.world.level.block.entity.BlockEntity.class);
        var markings = (willow.train.hexie.blocks.parts.VehicleMarkings) target;
        pos=target.getBlockPos(); initialFields=markings.fields(); initialLeft=markings.descriptionLeft();
    }
    public static boolean openMoving(net.minecraft.server.level.ServerPlayer player, Target moving) {
        if (!(moving.entity() instanceof willow.train.hexie.blocks.parts.VehicleMarkings markings) || !moving.stillValid(player)) return false;
        player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, p) -> {
            var menu = new CarriageMarkingMenu(id, inv, moving.entity());
            menu.attachTarget(moving);
            return menu;
        }, net.minecraft.network.chat.Component.empty()), buf -> {
            buf.writeBlockPos(moving.entity().getBlockPos());
            for (String field : markings.fields()) buf.writeUtf(field, 128);
            buf.writeBoolean(markings.descriptionLeft());
            buf.writeBoolean(true);
        });
        return true;
    }
    @Override public boolean stillValid(Player player) {
        if (contentOnly()) return super.stillValid(player);
        if (!player.mayBuild() || player.isSpectator() || !player.level().hasChunkAt(pos)
                || !(player.getMainHandItem().getItem() instanceof ColoredBrush || player.getOffhandItem().getItem() instanceof ColoredBrush)
                || !(player.level().getBlockEntity(pos) instanceof willow.train.hexie.blocks.parts.VehicleMarkings)
                || !(player.level().getBlockState(pos).getBlock() instanceof willow.train.hexie.blocks.parts.VehicleMarkingSurface block)
                || !block.markingRoot(player.level(),pos,player.level().getBlockState(pos)).equals(pos)
                || target!=null && (player.level().getBlockEntity(pos)!=target || target.isRemoved())) return false;
        var bounds=block.markingRenderBounds(player.level().getBlockState(pos)).move(pos).inflate(6);
        return bounds.contains(player.getEyePosition());
    }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
