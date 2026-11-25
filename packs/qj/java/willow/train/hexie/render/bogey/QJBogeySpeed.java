package willow.train.hexie.render.bogey;

import net.minecraft.nbt.CompoundTag;

// split from willow/train/hexie/client/render/bogey/DF11GBogeyRenderer.java, lines 18-20, 558-598
final class QJBogeySpeed {
    public static final String
            PREVIOUS_SPEED_FACTOR = "PreviousSpeedFactor",
            RECENT_SPEED_FACTOR = "RecentSpeedFactor";

    public static double getDTHITA(double x){

        if (x >= 0 && x < 0.1){
            return Math.sin(2 * Math.PI * (5 * x - 0.25)) / 2 + 0.5;
        } else if (x >= 0.1 && x < 0.5){
            return 1;
        } else if (x >= 0.5 && x < 0.667){
            return Math.sin(2 * Math.PI * (3 * x - 0.25)) / 4 + 0.75;
        } else if (x > 0.667){
            return 0.5;
        } else {
            return x;
        }
    }

    /**
     * 这个方法用以维护 bogeyData 里的速度数据和动画Tick数据
     * @param nbt 需要被维护的 bogeyData 数据
     * @param recent_speed 当前的速度值
     */
    public static void updateCompoundTagSpeedData(CompoundTag nbt, float recent_speed) {
        if(nbt.getFloat(RECENT_SPEED_FACTOR) != recent_speed){
            nbt.putInt("counter", 0);
            nbt.putFloat(PREVIOUS_SPEED_FACTOR, nbt.getFloat(RECENT_SPEED_FACTOR));
            nbt.putFloat(RECENT_SPEED_FACTOR, recent_speed);
        }
    }

    public static float getActualSpeedRatioLazy(CompoundTag nbt, float max_speed) {
        if(!nbt.contains("recent")) {
            nbt.putFloat("recent", nbt.getFloat(PREVIOUS_SPEED_FACTOR));
            return nbt.getFloat(PREVIOUS_SPEED_FACTOR) / max_speed;
        } else {
            if((Math.abs(nbt.getFloat("recent")) - Math.abs(nbt.getFloat(RECENT_SPEED_FACTOR)))
                    * Math.signum(nbt.getFloat("recent") - nbt.getFloat(RECENT_SPEED_FACTOR)) > 0.01f) {
                nbt.putFloat("recent", nbt.getFloat("recent") +
                        (nbt.getFloat(RECENT_SPEED_FACTOR) - nbt.getFloat(PREVIOUS_SPEED_FACTOR)) * .1f);
                return nbt.getFloat("recent") / max_speed;
            } else return nbt.getFloat(RECENT_SPEED_FACTOR)/max_speed;
        }
    }
}
