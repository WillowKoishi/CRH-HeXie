package willow.train.hexie.blockentity;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.init.BlockEntitiesInit;
import willow.train.hexie.menu.GroundStationSignMenu;
import java.util.List;

public class GroundStationSignEntity extends SmartBlockEntity implements MenuProvider {
    public static final int MAX_TEXT = 64;
    public static final int FONT_COUNT = 3;
    private int fontStyle;
    public int fontStyle() { return fontStyle; }
    public void cycleFont() { fontStyle = (fontStyle + 1) % FONT_COUNT; setChanged(); sendData(); }
    private static final String[] KEYS = {"StationName", "Pinyin", "PreviousStation", "NextStation"};
    private final String[] fields = {"", "", "", ""};
    public GroundStationSignEntity(BlockPos pos, BlockState state) { super(BlockEntitiesInit.GROUND_STATION_SIGN.get(), pos, state); }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}
    public String[] fields() { return fields.clone(); }
    public static String clean(String value) {
        StringBuilder out = new StringBuilder();
        value.codePoints().filter(c -> !Character.isISOControl(c) && c != 0xA7 && Character.getType(c) != Character.FORMAT)
                .limit(MAX_TEXT).forEach(out::appendCodePoint);
        return out.toString();
    }
    public void update(String[] values) {
        for (int i = 0; i < fields.length; i++) fields[i] = clean(values[i]);
        setChanged(); sendData();
    }
    public void writeFields(FriendlyByteBuf buf) { for (String field : fields) buf.writeUtf(field, MAX_TEXT * 2); }
    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("FontStyle", fontStyle);
        for (int i = 0; i < fields.length; i++) tag.putString(KEYS[i], fields[i]);
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        int savedStyle = tag.getInt("FontStyle");
        fontStyle = savedStyle >= 0 && savedStyle < FONT_COUNT ? savedStyle : 0;
        for (int i = 0; i < fields.length; i++) fields[i] = clean(tag.getString(KEYS[i]));
    }
    @Override public Component getDisplayName() { return Component.translatable("block.hexie.ground_station_sign"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new GroundStationSignMenu(id, inventory, worldPosition, fields()); }
}
