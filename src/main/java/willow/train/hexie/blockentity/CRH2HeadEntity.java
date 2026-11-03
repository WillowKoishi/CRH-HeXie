package willow.train.hexie.blockentity;
import net.minecraft.core.BlockPos;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import willow.train.hexie.menu.HeadNumberMenu;
import java.util.List;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.init.BlockEntitiesInit;
public final class CRH2HeadEntity extends SmartBlockEntity implements MenuProvider {
    public static final int MAX_LENGTH = 32;
    private String number = "";
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}
    public String number() { return number; }
    public static String clean(String text) {
        return text.codePoints().filter(c -> !Character.isISOControl(c)
                && Character.getType(c) != Character.FORMAT && c != 0xA7)
                .limit(MAX_LENGTH).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }
    public void updateNumber(String text) { number = clean(text); setChanged(); sendData(); }
    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket); tag.putString("HeadNumber", number);
    }
    @Override public void writeSafe(CompoundTag tag,HolderLookup.Provider registries) {
        super.writeSafe(tag,registries);tag.putString("HeadNumber",number);
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket); number = clean(tag.getString("HeadNumber"));
    }
    @Override public Component getDisplayName() { return Component.translatable("gui.hexie.head_number.title"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new HeadNumberMenu(id, inventory, this);
    }
    public CRH2HeadEntity(BlockPos p,BlockState s) { super(BlockEntitiesInit.CRH2A_HEAD.get(),p,s); }
}
