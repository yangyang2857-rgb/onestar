package com.yangyang2857rgb.onestar;

import java.util.Calendar;
import java.util.List;
import java.util.Map;

/**
 * 拾光 v4.0「心情曲线」：把散落的每天，折算成一条可看的线。
 * 纯逻辑，不依赖 android，可直接在桌面 JVM 上单测。
 *
 * 取值约定：返回数组值域 0..1（0 = 低落，1 = 炽烈），无数据的桶为 NaN。
 * 心情序号与 Constellation.MOOD_COLOR 对齐：0 静谧 1 雀跃 2 温柔 3 低落 4 炽烈。
 */
final class MoodCurve {

    private static final float[] WEIGHT = {0.55f, 0.85f, 0.70f, 0.15f, 1.0f};

    private MoodCurve() {
    }

    static float weightOf(int moodIdx) {
        if (moodIdx < 0 || moodIdx >= WEIGHT.length) return 0.55f;
        return WEIGHT[moodIdx];
    }

    /**
     * 最近 weeks 周（含本周）的情绪折线，从左到右由旧到新。
     *
     * @param days  已按日期升序排列的日期串
     * @param today 今天 yyyy-MM-dd
     */
    static float[] weekly(List<String> days, Map<String, Integer> mood, String today, int weeks) {
        if (weeks <= 0) return new float[0];
        float[] sum = new float[weeks];
        int[] cnt = new int[weeks];
        long now = Constellation.parseDate(today);
        if (now <= 0) now = System.currentTimeMillis();
        for (String d : days) {
            long t = Constellation.parseDate(d);
            if (t <= 0 || t > now) continue;
            long diff = (now - t) / 86400000L;
            int bucket = weeks - 1 - (int) (diff / 7);
            if (bucket < 0 || bucket >= weeks) continue;
            Integer m = mood.get(d);
            sum[bucket] += weightOf(m == null ? 0 : m);
            cnt[bucket]++;
        }
        float[] out = new float[weeks];
        for (int i = 0; i < weeks; i++) {
            out[i] = cnt[i] == 0 ? Float.NaN : sum[i] / cnt[i];
        }
        // v5.2：原生三抽头平滑（libons_curve.so），让折线转折不那么生硬
        if (NativeCore.CURVE && out.length > 2) {
            try {
                NativeCore.soften(out, out.length, 2);
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    /** 折线里连续有数据的段落，返回 [start, end] 索引对（用于跳过空缺不连线）。 */
    static int[][] segments(float[] series) {
        int n = series == null ? 0 : series.length;
        int segs = 0;
        for (int i = 0; i < n; i++) {
            if (!Float.isNaN(series[i]) && (i == 0 || Float.isNaN(series[i - 1]))) segs++;
        }
        int[][] out = new int[segs][];
        int k = -1;
        for (int i = 0; i < n; i++) {
            boolean has = !Float.isNaN(series[i]);
            boolean prev = i > 0 && !Float.isNaN(series[i - 1]);
            if (has && !prev) {
                k++;
                out[k] = new int[]{i, i};
            } else if (has) {
                out[k][1] = i;
            }
        }
        return out;
    }

    /** 折线的一句话总结，如「这六周，偏亮」。 */
    static String caption(float[] series) {
        int n = 0;
        float sum = 0;
        for (float v : series == null ? new float[0] : series) {
            if (!Float.isNaN(v)) {
                sum += v;
                n++;
            }
        }
        if (n == 0) return "这六周，还没有颜色";
        float avg = sum / n;
        if (avg >= 0.82f) return "这六周，偏炽烈";
        if (avg >= 0.68f) return "这六周，偏亮";
        if (avg >= 0.48f) return "这六周，平稳";
        if (avg >= 0.30f) return "这六周，偏静";
        return "这六周，偏低落";
    }

    /** 六周前那天的日期（用于横轴标注）。 */
    static String weeksAgoLabel(String today, int weeks) {
        long t = Constellation.parseDate(today);
        if (t <= 0) return "";
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t - Math.max(0, weeks - 1) * 7L * 86400000L);
        return (c.get(Calendar.MONTH) + 1) + "/" + c.get(Calendar.DAY_OF_MONTH);
    }
}
