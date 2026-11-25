package willow.train.hexie.world.blocks.emu;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** One game-time timeline for the covers and the telescoping coupler front. */
public final class CRH1HeadAnimation {
    public static final String FROM = "CRH1HeadAnimationFrom";
    public static final String START = "CRH1HeadAnimationStart";
    public static final String TARGET = "CRH1HeadAnimationTarget";
    public static final double DURATION = 32;
    public static final double EXTENSION = .350;

    private CRH1HeadAnimation() {}

    public static float progress(BlockState state, CompoundTag data, double time) {
        return progress(state.getValue(BlockStateProperties.OPEN), data, time);
    }
    public static float progress(boolean opened, CompoundTag data, double time) {
        float target = data != null && data.contains(TARGET, Tag.TAG_BYTE)
                ? (data.getBoolean(TARGET) ? 1 : 0) : opened ? 1 : 0;
        if (data == null || !data.contains(FROM, Tag.TAG_ANY_NUMERIC) || !data.contains(START, Tag.TAG_ANY_NUMERIC))
            return target;
        float from = Mth.clamp(data.getFloat(FROM), 0, 1);
        double elapsed = Math.max(0, time - data.getLong(START));
        return Mth.approach(from, target, (float) (elapsed / DURATION));
    }

    public static float withdraw(float progress) { return Mth.clamp(progress / .125f, 0, 1); }
    public static float open(float progress) { return Mth.clamp((progress - .125f) / .625f, 0, 1); }
    public static float extension(float progress) { return Mth.clamp((progress - .75f) / .25f, 0, 1); }
    public static boolean ready(BlockState state, CompoundTag data, double time) {
        return ready(state.getValue(BlockStateProperties.OPEN), data, time);
    }
    public static boolean ready(boolean opened, CompoundTag data, double time) {
        return opened && extension(progress(opened, data, time)) >= 1;
    }

    public static StructureTemplate.StructureBlockInfo toggle(StructureTemplate.StructureBlockInfo info, long now) {
        return synchronize(info, progress(info.state(), info.nbt(), now), now,
                !info.state().getValue(BlockStateProperties.OPEN));
    }

    /** Apply the authoritative timeline to captured data on either side of a carriage. */
    public static StructureTemplate.StructureBlockInfo synchronize(StructureTemplate.StructureBlockInfo info, float from, long start, boolean open) {
        var data = info.nbt() == null ? new CompoundTag() : info.nbt().copy();
        if (!data.contains("id")) data.putString("id", "hexie:crh1a_head");
        data.putFloat(FROM, Mth.clamp(from, 0, 1));
        data.putLong(START, start);
        data.putBoolean(TARGET, open);
        return new StructureTemplate.StructureBlockInfo(info.pos(), info.state().setValue(BlockStateProperties.OPEN, open), data);
    }
}
