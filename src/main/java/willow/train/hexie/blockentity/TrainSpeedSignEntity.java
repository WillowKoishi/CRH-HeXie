package willow.train.hexie.blockentity;


import com.simibubi.create.content.equipment.clipboard.ClipboardCloneable;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import willow.train.hexie.menu.TrainSpeedSignEditMenu;
import willow.train.hexie.init.BlockEntitiesInit;
import willow.train.hexie.network.HexieNetworkHandler;
import willow.train.hexie.network.c2s.signs.TrainSpeedSignUpdatePacket;

import java.util.List;
import java.util.function.Function;

public class TrainSpeedSignEntity extends SmartBlockEntity
        implements MenuProvider, ClipboardCloneable {

    private float x_offset = 0, y_offset = 0;
    private int color = CarriageTypeSignEntity.RED;
    private Component content = Component.literal("xx km/h");

    private boolean x_revert = false, y_revert = false;

    public TrainSpeedSignEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public TrainSpeedSignEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesInit.TRAIN_SPEED_SIGN.get(), pos, state);
    }

    public FormattedCharSequence getRenderMessages(
            Function<Component, FormattedCharSequence> pMessageTransformer) {
        return pMessageTransformer.apply(this.content);
    }

    @Override
    protected void write(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putString("content", content.getString());
        tag.putInt("color", color);
        tag.putFloat("x_offset", x_offset);
        tag.putFloat("y_offset", y_offset);
        tag.putBoolean("x_revert", x_revert);
        tag.putBoolean("y_revert", y_revert);
    }

    @Override
    protected void read(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("content"))
            this.content =
                    Component.literal(tag.getString("content"));
        if (tag.contains("color")) this.color = tag.getInt("color");
        if (tag.contains("x_offset")) this.x_offset = tag.getFloat("x_offset");
        if (tag.contains("y_offset")) this.y_offset = tag.getFloat("y_offset");
        if (tag.contains("x_revert")) this.x_revert = tag.getBoolean("x_revert");
        if (tag.contains("y_revert")) this.y_revert = tag.getBoolean("y_revert");
    }

    public void markUpdated(boolean isClientSide) {
        this.setChanged();
        if (isClientSide) {
            HexieNetworkHandler.sendToServer(
                    new TrainSpeedSignUpdatePacket(
                            this.getBlockPos(),
                            content,
                            color,
                            x_offset,
                            y_offset,
                            x_revert,
                            y_revert));
        } else {
            sendData();
        }
        this.level.sendBlockUpdated(
                this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return super.getUpdateTag(registries);
    }

    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public Component getContent() {
        return content;
    }

    public void setContent(Component content) {
        this.content = content;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public boolean isX_revert() {
        return x_revert;
    }

    public boolean isY_revert() {
        return y_revert;
    }

    public void setX_revert(boolean x_revert) {
        this.x_revert = x_revert;
    }

    public void setY_revert(boolean y_revert) {
        this.y_revert = y_revert;
    }

    public void setX_offset(float x_offset) {
        this.x_offset = x_offset;
    }

    public float getX_offset() {
        return x_offset;
    }

    public void setY_offset(float y_offset) {
        this.y_offset = y_offset;
    }

    public boolean revert_x() {
        this.x_revert = !x_revert;
        return x_revert;
    }

    public boolean revert_y() {
        this.y_revert = !y_revert;
        return y_revert;
    }

    public float getY_offset() {
        return y_offset;
    }

    @Override
    public String getClipboardKey() {
        return "train_speed_sign_entity";
    }

    @Override
    public boolean writeToClipboard(net.minecraft.core.HolderLookup.Provider registries, CompoundTag tag, Direction side) {
        tag.putString("sign", "train_speed_sign");
        tag.putString("content", content.getString());
        tag.putInt("color", color);
        tag.putFloat("x_offset", x_offset);
        tag.putFloat("y_offset", y_offset);
        tag.putBoolean("x_revert", x_revert);
        tag.putBoolean("y_revert", y_revert);
        return true;
    }

    @Override
    public boolean readFromClipboard(
            net.minecraft.core.HolderLookup.Provider registries, CompoundTag tag, Player player, Direction side, boolean simulate) {
        if (simulate) return true;
        if (tag.contains("sign") && tag.getString("sign").equals("train_speed_sign")) {
            this.content =
                    Component.literal(tag.getString("content"));
            this.color = tag.getInt("color");
            this.x_offset = tag.getFloat("x_offset");
            this.y_offset = tag.getFloat("y_offset");
            this.x_revert = tag.getBoolean("x_revert");
            this.y_revert = tag.getBoolean("y_revert");
            markUpdated(false);
            return true;
        }
        return false;
    }

    @Override
    public Component getDisplayName() {
        return Component.empty();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(
            int pContainerId, Inventory pPlayerInventory, Player pPlayer) {
        TrainSpeedSignEditMenu menu =
                new TrainSpeedSignEditMenu(
                        pContainerId, pPlayerInventory, this, new SimpleContainerData(2));
        menu.setTsse(this);
        return menu;
    }
}
