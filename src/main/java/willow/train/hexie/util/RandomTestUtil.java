package willow.train.hexie.util;

import willow.train.hexie.Main;

public class RandomTestUtil {

    /**
     * <p>这个方法返回的是随机数是否落在[0, targetProbability)的区间内，取样空间为 [0, 100)</p>
     * @param targetProbability 目标概率，范围取在 [0, 100) 之间
     * @return 目标是否命中指定区间
     */
    public static boolean randomBoolean(float targetProbability) {
        targetProbability /= 100;
        if(targetProbability < 0 || targetProbability >= 1) return false;
        return Main.HEXIE_RANDOM.nextFloat(0, 1) < targetProbability;
    }

    /**
     * <p>这个方法进行负二项分布随机试验，即进行 n 次伯努利试验，只要当某一次实验为假即返回迭代次数。
     * <br>若所有实验均为真，即实验全通过的情况下，返回实验总次数<p/>
     * @param targetProbability 目标概率，范围取在 [0, 100) 之间
     * @param experimentTimes 总的试验次数
     * @return 命中层数，取值为 [0, experimentTimes]，类型为 int
     */
    public static int negativeBinomialRandomExperiment(float targetProbability, int experimentTimes) {
        for(int i = 0; i < experimentTimes; i++)
            if(!randomBoolean(targetProbability)) return i;
        return experimentTimes;
    }
}
