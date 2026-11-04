package com.yangyang2857rgb.onestar;

/**
 * 拾光 v4.0「星轨回放」：把点亮过的日子，按顺序重新走一遍。
 * 只负责时间轴算术（时长 / 进度 / 当前索引 / 缓动），绘制交给视图层。
 * 纯逻辑，不依赖 android，可直接在桌面 JVM 上单测。
 */
final class Replay {

    /** 单颗星占用的时长（毫秒） */
    static final long MS_PER_STAR = 110;
    /** 最短总时长 */
    static final long MIN_MS = 2600;
    /** 最长总时长 */
    static final long MAX_MS = 9000;

    private Replay() {
    }

    /** 总时长：星多则走久一点，但不超过 9 秒。 */
    static long durationOf(int stars) {
        if (stars <= 0) return 0;
        long d = stars * MS_PER_STAR;
        if (d < MIN_MS) d = MIN_MS;
        if (d > MAX_MS) d = MAX_MS;
        return d;
    }

    /** 进度 0..1 映射到当前正在点亮第几颗（0-based）；无星返回 -1。 */
    static int indexAt(float progress, int stars) {
        if (stars <= 0) return -1;
        if (progress <= 0) return 0;
        if (progress >= 1f) return stars - 1;
        int i = (int) Math.floor(progress * stars);
        if (i < 0) i = 0;
        if (i > stars - 1) i = stars - 1;
        return i;
    }

    /** 已走过的进度（0..1），用于画轨迹长度。 */
    static float progressOf(long elapsed, long duration) {
        if (duration <= 0) return 1f;
        float p = elapsed / (float) duration;
        return p < 0 ? 0 : (p > 1f ? 1f : p);
    }

    /** 缓动（先快后慢），让轨迹生长有呼吸感。 */
    static float ease(float p) {
        if (p <= 0) return 0;
        if (p >= 1) return 1;
        return 1 - (float) Math.pow(1 - p, 2.6);
    }

    /** 把一次拖动换算成播放位置（秒级 UI 用），返回 0..1。 */
    static float seek(float touchX, float left, float right) {
        if (right - left <= 0) return 0;
        float p = (touchX - left) / (right - left);
        return p < 0 ? 0 : (p > 1f ? 1f : p);
    }

    /** 回放时顶部的日期标题，如「2026 · 04 · 18」。 */
    static String caption(String date) {
        if (date == null || date.length() < 10) return "";
        return date.substring(0, 4) + " · " + date.substring(5, 7) + " · " + date.substring(8, 10);
    }
}
