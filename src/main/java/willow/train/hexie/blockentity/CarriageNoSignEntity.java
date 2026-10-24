package willow.train.hexie.blockentity;


import com.simibubi.create.content.equipment.clipboard.ClipboardCloneable;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import willow.train.hexie.menu.CarriageNoSignEditMenu;
import willow.train.hexie.init.BlockEntitiesInit;
import willow.train.hexie.network.HexieNetworkHandler;
import willow.train.hexie.network.c2s.signs.CarriageNoSignUpdatePacket;

import java.util.List;
import java.util.function.Function;

/**
 * Entity 和 Block 的连接在 init/BlockEntitiesInit 里 (注册的时候就写明) Entity 和 Renderer 的连接在 Main 下 如
 * BlockEntityRenderers.register(BlockEntitiesInit.CARRIAGE_NO_SIGN.get(),
 * CarriageNoSignRenderer::new); 这个 Entity 是根据 Block.newBlockEntity() 来调用的，记得在对应的 Block 里写明这个方法 这个
 * Entity 对应的 Block 是 TrainNoBlock ，在 Blocks/Signs 包内
 */
public class CarriageNoSignEntity extends SmartBlockEntity
        implements MenuProvider, ClipboardCloneable {

    private int color = CarriageTypeSignEntity.RED;

    private boolean isLeftSide = true;
    private Component message = Component.literal("0");

    public CarriageNoSignEntity(BlockPos pPos, BlockState pBlockState) {
        super(BlockEntitiesInit.CARRIAGE_NO_SIGN.get(), pPos, pBlockState);
    }

    public FormattedCharSequence getRenderMessages(
            Function<Component, FormattedCharSequence> pMessageTransformer) {
        return pMessageTransformer.apply(this.message);
    }

    public void setLeftSide(boolean leftSide) {
        isLeftSide = leftSide;
    }

    public boolean isLeftSide() {
        return isLeftSide;
    }

    public void mirror() {
        this.isLeftSide = !this.isLeftSide;
    }

    public int getColor() {
        return this.color;
    }

    public boolean setColor(int mark) {
        if (mark != this.getColor()) {
            this.color = mark;
            return true;
        } else {
            return false;
        }
    }

    public void setMessage(Component message) {
        this.message = message;
    }

    public Component getMessage() {
        return this.message;
    }

    @Override
    protected void write(CompoundTag pTag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.write(pTag, registries, clientPacket);
        pTag.putString("message", message.getString());
        pTag.putBoolean("left", isLeftSide);
        pTag.putInt("color", color);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    @Override
    public void read(CompoundTag pTag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.read(pTag, registries, clientPacket);
        if (pTag.contains("message"))
            this.message =
                    Component.literal(pTag.getString("message"));
        if (pTag.contains("left")) this.isLeftSide = pTag.getBoolean("left");
        if (pTag.contains("color")) this.color = pTag.getInt("color");
    }

    public void markUpdated(boolean isClientSide) {
        this.setChanged();
        if (isClientSide) {
            HexieNetworkHandler.sendToServer(
                    new CarriageNoSignUpdatePacket(this.getBlockPos(), message, color, isLeftSide));
        } else {
            sendData();
        }
        this.level.sendBlockUpdated(
                this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
    }

    /**
     * 这个方法用以自动保存所需信息，只要像这样重写一次即可
     *
     * @return updateTag
     */
    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return super.getUpdateTag(registries);
    }

    /**
     * 这个方法是接收更新包用的，像这样重写即可
     *
     * @return updatePacket 更新包裹
     */
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return Component.empty();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(
            int pContainerId, Inventory pPlayerInventory, Player pPlayer) {
        CarriageNoSignEditMenu cnses =
                new CarriageNoSignEditMenu(
                        pContainerId, pPlayerInventory, this, new SimpleContainerData(2));
        cnses.setCtse(this);
        return cnses;
    }

    public void sendData() {
        if (level instanceof ServerLevel serverLevel)
            serverLevel.getChunkSource().blockChanged(getBlockPos());
    }

    @Override
    public String getClipboardKey() {
        return "carriage_no_sign";
    }

    @Override
    public boolean writeToClipboard(net.minecraft.core.HolderLookup.Provider registries, CompoundTag tag, Direction side) {
        tag.putString("sign", "carriage_no_sign");
        tag.putString("message", message.getString());
        tag.putBoolean("left", isLeftSide);
        tag.putInt("color", color);
        return true;
    }

    @Override
    public boolean readFromClipboard(
            net.minecraft.core.HolderLookup.Provider registries, CompoundTag tag, Player player, Direction side, boolean simulate) {
        if (simulate) return true;
        if (!tag.contains("sign")
                || !tag.contains("message")
                || !tag.contains("left")
                || !tag.contains("color")) return false;
        if (!tag.getString("sign").equals("carriage_no_sign")) return false;
        this.color = tag.getInt("color");
        this.message = Component.literal(tag.getString("message"));
        this.isLeftSide = tag.getBoolean("left");
        markUpdated(false);
        return true;
    }
}
