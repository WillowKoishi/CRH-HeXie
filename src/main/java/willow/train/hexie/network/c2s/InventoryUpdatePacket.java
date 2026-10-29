package willow.train.hexie.network.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import willow.train.hexie.Main;

import java.util.ArrayList;
import java.util.List;

public class InventoryUpdatePacket implements HexiePacket {

    public static Logger logger = Main.LOGGER;

    private final List<Integer> dirtyIndex;
    private final List<ItemStack> dirtyItems;

    public InventoryUpdatePacket(int dirtySlot, ItemStack newItemStack) {
        this(List.of(dirtySlot), List.of(newItemStack));
    }

    public InventoryUpdatePacket(List<Integer> dirty, List<ItemStack> dirtyItems) {
        // Encoding can run after the screen clears its list or changes an item.
        this.dirtyIndex = List.copyOf(dirty);
        this.dirtyItems = dirtyItems.stream().map(ItemStack::copy).toList();
    }

    public InventoryUpdatePacket(FriendlyByteBuf buf) {
        int size = buf.readInt();
        dirtyIndex = new ArrayList<>();
        dirtyItems = new ArrayList<>();
        for(int i = 0; i < size; i++) {
            dirtyIndex.add(buf.readInt());
            dirtyItems.add(ItemStack.OPTIONAL_STREAM_CODEC.decode((net.minecraft.network.RegistryFriendlyByteBuf) buf));
        }
    }
    @Override
    public boolean handle(IPayloadContext var1) {
        var1.enqueueWork(
                () -> {
                    if(((net.minecraft.server.level.ServerPlayer) var1.player()) == null) return;
                    ServerPlayer player = ((net.minecraft.server.level.ServerPlayer) var1.player()).connection.getPlayer();

                    Inventory inventory = player.getInventory();
                    int counter = 0;
                    for(Integer index : dirtyIndex) {
                        inventory.setItem(index, dirtyItems.get(counter));
                        counter++;
                    }
                }
        );
        return true;
    }

    @Override
    public void encode(FriendlyByteBuf var1) {
        if(dirtyIndex.size() != dirtyItems.size()) {
            logger.error("The list length is inconsistent with the list parameter passing");
            logger.debug("Length of list : " + dirtyIndex.size());
            logger.debug("Length of Parameters : " + dirtyItems.size());
            logger.error("-- From network.c2s.InventoryUpdatePacket");
            var1.writeInt(0);
            return;
        }
        var1.writeInt(dirtyIndex.size());
        int counter = 0;
        for(Integer index : dirtyIndex) {
            var1.writeInt(index);
            ItemStack.OPTIONAL_STREAM_CODEC.encode((net.minecraft.network.RegistryFriendlyByteBuf) var1, dirtyItems.get(counter));
            counter++;
        }
    }
}
