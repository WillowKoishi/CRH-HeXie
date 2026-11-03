package willow.train.hexie.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import willow.train.hexie.blockentity.CRH2PartEntity;
import willow.train.hexie.blocks.locos.crh2a.CRH2PanelBlock;
import willow.train.hexie.init.client.MenuInit;
import willow.train.hexie.item.editable_panels.ColoredBrush;

public final class CarriageMarkingMenu extends BlockEntityEditMenu<net.minecraft.world.level.block.entity.BlockEntity> {
    public final BlockPos pos;
    public final String[] initialFields;
    public final boolean initialLeft;
    public CarriageMarkingMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        super(MenuInit.CARRIAGE_MARKING_MENU.get(), id, null, net.minecraft.world.level.block.entity.BlockEntity.class);
        pos=buf.readBlockPos(); initialFields=new String[]{buf.readUtf(128),buf.readUtf(128),buf.readUtf(128)};
        initialLeft=buf.readBoolean(); target=null;
    }
    public CarriageMarkingMenu(int id, Inventory inventory, net.minecraft.world.level.block.entity.BlockEntity target) {
        super(MenuInit.CARRIAGE_MARKING_MENU.get(), id, target, net.minecraft.world.level.block.entity.BlockEntity.class);
        var markings = (willow.train.hexie.blocks.parts.VehicleMarkings) target;
        pos=target.getBlockPos(); initialFields=markings.fields(); initialLeft=markings.descriptionLeft();
    }
    @Override public boolean stillValid(Player player) {
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
