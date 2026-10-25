package willow.train.hexie.blockentity;

import com.simibubi.create.content.equipment.clipboard.ClipboardCloneable;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import willow.train.hexie.Main;
import willow.train.hexie.menu.LacqueredBoardEditMenu;
import willow.train.hexie.init.BlockEntitiesInit;
import willow.train.hexie.network.HexieNetworkHandler;
import willow.train.hexie.network.c2s.signs.LacqueredBoardPacket;

import java.awt.*;
import java.util.List;
import java.util.function.Function;

public class LacqueredBoardEntity extends SmartBlockEntity
        implements MenuProvider, ClipboardCloneable {

    private final Component[] messages =
            new Component[] {
                Component.empty(),
                Component.empty(),
                Component.empty(),
                Component.empty(),
                Component.empty(),
                Component.empty(),
                Component.empty(),
                Component.empty(),
                Component.empty(),
                Component.empty(),
                Component.empty(),
                Component.empty(),
                Component.empty(),
                Component.empty(),
                Component.empty(),
                Component.empty()
            };
    // 一共预留了16个数据槽位
    private FormattedCharSequence[] renderMessages;

    public static final float WIDTH = 1.0f, HEIGHT = 0.2f; // 水牌的宽高
    public static final Component BELT = Component.literal("■"); // 充当丝带和背景板的占位符
    public static final int NUMBER_OF_TYPES = 1; // 水牌的种类数

    private double x_offset = 0;
    private int backGroundColor = 15216648,
            forGroundColor = 0x0,
            beltForGroundColor = 0xffffff; // 背景色和前景色
    private int type = 0; // 当前处于的种类

    public LacqueredBoardEntity(BlockEntityType<?> pType, BlockPos pPos, BlockState pBlockState) {
        super(pType, pPos, pBlockState);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public LacqueredBoardEntity(BlockPos pPos, BlockState pBlockState) {
        this(BlockEntitiesInit.LAQUERED_BOARD.get(), pPos, pBlockState);
    }

    @Override
    public Component getDisplayName() {
        return Component.empty();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(
            int pContainerId, Inventory pPlayerInventory, Player pPlayer) {
        LacqueredBoardEditMenu menu =
                new LacqueredBoardEditMenu(
                        pContainerId, pPlayerInventory, this, new SimpleContainerData(2));
        menu.setLbe(this);
        return menu;
    }

    public void write(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("type", type);
        for (int i = 0; i < messages.length; i++) {
            tag.putString("content " + i, messages[i].getString());
        }
        tag.putInt("beltColor", backGroundColor);
        tag.putInt("textColor", forGroundColor);
        tag.putInt("pinyinColor", beltForGroundColor);
        tag.putDouble("x_offset", x_offset);
    }

    public void read(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.type = tag.getInt("type");
        for (int i = 0; i < messages.length; i++) {
            if (tag.contains("content " + i))
                messages[i] =
                        Component.literal(tag.getString("content " + i));
        }
        if (tag.contains("beltColor")) this.backGroundColor = tag.getInt("beltColor");
        if (tag.contains("textColor")) this.forGroundColor = tag.getInt("textColor");
        if (tag.contains("pinyinColor")) this.beltForGroundColor = tag.getInt("pinyinColor");
        if (tag.contains("x_offset")) this.x_offset = tag.getDouble("x_offset");
    }

    public double getXOffset() {
        return x_offset;
    }

    public void setXOffset(double offset) {
        this.x_offset = offset;
    }

    public void setMessages(String[] messages) {
        int length =
                (messages.length > this.messages.length) ? this.messages.length : messages.length;
        for (int i = 0; i < length; i++) {
            this.messages[i] = Component.literal(messages[i]);
        }
    }

    public void setColors(int... colors) {
        if (colors.length != 3) {
            Main.LOGGER.error("Incompatible color number!");
            return;
        }
        this.backGroundColor = colors[0];
        this.forGroundColor = colors[1];
        this.beltForGroundColor = colors[2];
    }

    public Component getMessage(int index) {
        return this.messages[index];
    }

    public int getBackGroundColor() {
        return backGroundColor;
    }

    public int getForGroundColor() {
        return forGroundColor;
    }

    public int getBeltForGroundColor() {
        return beltForGroundColor;
    }

    public FormattedCharSequence[] getRenderMessages(
            Function<Component, FormattedCharSequence> pMessageTransformer, int messageCount) {

        int count = Math.max(0, Math.min(messageCount, this.messages.length));
        this.renderMessages = new FormattedCharSequence[count + 1];
        this.renderMessages[0] = pMessageTransformer.apply(BELT);
        for (int i = 1; i <= count; ++i) {
            this.renderMessages[i] = pMessageTransformer.apply(this.messages[i - 1]);
        }
        return this.renderMessages;
    }

    public int getBoardType() {
        return type;
    }

    public void markUpdated(boolean isClientSide) {
        this.setChanged();
        if (isClientSide) {
            HexieNetworkHandler.sendToServer(
                    new LacqueredBoardPacket(
                            this.getBlockPos(),
                            this.messages,
                            this.x_offset,
                            this.backGroundColor,
                            this.forGroundColor,
                            this.beltForGroundColor));
        } else {
            sendData();
        }
        this.level.sendBlockUpdated(
                this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
    }

    @Override
    public String getClipboardKey() {
        return "laquered_board";
    }

    @Override
    public boolean writeToClipboard(net.minecraft.core.HolderLookup.Provider registries, CompoundTag tag, Direction side) {
        tag.putString("sign", "laquered_board");
        tag.putInt("type", type);
        for (int i = 0; i < messages.length; i++) {
            tag.putString("content " + i, messages[i].getString());
        }
        tag.putInt("beltColor", backGroundColor);
        tag.putInt("textColor", forGroundColor);
        tag.putInt("pinyinColor", beltForGroundColor);
        tag.putDouble("x_offset", x_offset);
        return true;
    }

    @Override
    public boolean readFromClipboard(
            net.minecraft.core.HolderLookup.Provider registries, CompoundTag tag, Player player, Direction side, boolean simulate) {
        if (simulate) return true;
        if (tag.contains("sign")
                && tag.contains("type")
                && tag.contains("beltColor")
                && tag.contains("textColor")
                && tag.contains("pinyinColor")
                && tag.contains("x_offset")) {
            if (!tag.getString("sign").equals("laquered_board")) return false;
            for (int i = 0; i < this.messages.length; i++) {
                if (!tag.contains("content " + i)) return false;
            }
            this.type = tag.getInt("type");
            for (int i = 0; i < messages.length; i++) {
                messages[i] =
                        Component.literal(tag.getString("content " + i));
            }
            this.backGroundColor = tag.getInt("beltColor");
            this.forGroundColor = tag.getInt("textColor");
            this.beltForGroundColor = tag.getInt("pinyinColor");
            this.x_offset = tag.getDouble("x_offset");
            try {
                markUpdated(false);
            } catch (Exception e) {
            }
            return true;
        }
        return false;
    }

    public int[] getRGBAColor(int octColor) {
        int[] result = new int[4];
        Color color = new Color(octColor);
        result[0] = color.getRed();
        result[1] = color.getGreen();
        result[2] = color.getBlue();
        result[3] = color.getAlpha();
        return result;
    }
}
