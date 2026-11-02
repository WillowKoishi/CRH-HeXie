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

public final class CarriageMarkingMenu extends AbstractContainerMenu {
    public final BlockPos pos;
    public final String[] initialFields;
    public final boolean initialLeft;
    private final CRH2PartEntity target;
    public CarriageMarkingMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        super(MenuInit.CARRIAGE_MARKING_MENU.get(), id);
        pos=buf.readBlockPos(); initialFields=new String[]{buf.readUtf(128),buf.readUtf(128),buf.readUtf(128)};
        initialLeft=buf.readBoolean(); target=null;
    }
    public CarriageMarkingMenu(int id, Inventory inventory, CRH2PartEntity target) {
        super(MenuInit.CARRIAGE_MARKING_MENU.get(), id);
        this.target=target; pos=target.getBlockPos(); initialFields=target.fields(); initialLeft=target.descriptionLeft();
    }
    @Override public boolean stillValid(Player player) {
        if (!player.mayBuild() || player.isSpectator() || !player.level().hasChunkAt(pos)
                || !(player.getMainHandItem().getItem() instanceof ColoredBrush || player.getOffhandItem().getItem() instanceof ColoredBrush)
                || !(player.level().getBlockEntity(pos) instanceof CRH2PartEntity panel)
                || !(panel.getBlockState().getBlock() instanceof CRH2PanelBlock block)
                || panel.getBlockState().getValue(CRH2PanelBlock.PART)!=0
                || target!=null && (panel!=target || target.isRemoved())) return false;
        var bounds=block.placementBounds(panel.getBlockState()).move(pos).inflate(6);
        return bounds.contains(player.getEyePosition());
    }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
