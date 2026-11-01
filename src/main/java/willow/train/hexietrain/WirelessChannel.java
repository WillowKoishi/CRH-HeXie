package willow.train.hexietrain;

import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;

/**
 * 一个无线红石发送通道（对应转向架的一个面）。
 *
 * <p>Create 的无线红石是"每 tick 轮询发送强度"的模型：
 * 网络里同一频率的所有 {@link IRedstoneLinkable} 中取最大强度，
 * 再广播给所有"在听"的设备。我们只发送、不接收。
 */
final class WirelessChannel implements IRedstoneLinkable {

    private final TrainDispatcherBlockEntity be;
    private final SignalChannel channel;

    /** 我们不接收，但接口要求实现。 */
    private int received;

    WirelessChannel(TrainDispatcherBlockEntity be, SignalChannel channel) {
        this.be = be;
        this.channel = channel;
    }

    /** 发送强度 0~15 —— 由方块实体算好（刹车曲线 / 转向命令）。 */
    @Override
    public int getTransmittedStrength() {
        return be.output(channel);
    }

    @Override
    public void setReceivedStrength(int strength) {
        this.received = strength;
    }

    public int received() {
        return received;
    }

    /** 只发不收。 */
    @Override
    public boolean isListening() {
        return false;
    }

    @Override
    public boolean isAlive() {
        return !be.isRemoved() && be.hasLevel();
    }

    @Override
    public Couple<Frequency> getNetworkKey() {
        return be.networkKey(channel);
    }

    @Override
    public BlockPos getLocation() {
        return be.getBlockPos();
    }

    SignalChannel channel() {
        return channel;
    }
}