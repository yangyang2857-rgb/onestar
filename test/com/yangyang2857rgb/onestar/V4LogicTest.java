package com.yangyang2857rgb.onestar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 拾光 v4.0 纯逻辑单测（桌面 JVM，无需模拟器）：
 * 星名规整 / 心情曲线分桶 / 回放时间轴 / 小组件状态。
 * 因为相关类均为包级可见，本测试放在同包下编译。
 */
public class V4LogicTest {

    static int pass = 0;
    static int fail = 0;

    static void ok(String name, boolean cond) {
        if (cond) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL: " + name);
        }
    }

    static void eq(String name, Object a, Object b) {
        boolean same = (a == null && b == null) || (a != null && a.equals(b));
        if (!same) System.out.println("  got=" + a + " want=" + b);
        ok(name, same);
    }

    public static void main(String[] args) {
        // ---------- StarName ----------
        eq("name.clean.trim", StarName.clean("  小满 \n"), "小满");
        eq("name.clean.newline", StarName.clean("a\nb"), "a b");
        eq("name.clean.spaceCollapse", StarName.clean("a   b"), "a b");
        eq("name.clean.null", StarName.clean(null), "");
        eq("name.clean.max8", StarName.clean("一二三四五六七八九十"), "一二三四五六七八");
        eq("name.clean.emojiCount", StarName.clean("\uD83C\uDF1F\uD83C\uDF1F\uD83C\uDF1F"), "\uD83C\uDF1F\uD83C\uDF1F\uD83C\uDF1F");
        eq("name.fallback", StarName.fallback(7, 2), "第 7 颗 · 絮语");
        eq("name.fallback.badIdx", StarName.fallback(0, 1), "某颗星 · 跃光");
        eq("name.fallback.badMood", StarName.fallback(3, 99), "第 3 颗");
        eq("name.display.custom", StarName.display("小满", 7, 2), "小满");
        eq("name.display.blank", StarName.display("   ", 7, 2), "第 7 颗 · 絮语");
        eq("name.display.null", StarName.display(null, 1, 3), "第 1 颗 · 潮汐");

        // ---------- MoodCurve ----------
        List<String> days = new ArrayList<String>();
        Map<String, Integer> mood = new HashMap<String, Integer>();
        // 今天 2026-11-05，往前铺 42 天，每 3 天点亮一次
        String[] ds = {"2026-11-05", "2026-11-02", "2026-10-30", "2026-10-27", "2026-10-20",
                "2026-10-12", "2026-10-05", "2026-09-28", "2026-09-24", "2026-09-24"};
        int[] ms = {4, 1, 2, 0, 3, 1, 2, 3, 0, 4};
        for (int i = 0; i < ds.length; i++) {
            if (!days.contains(ds[i])) days.add(ds[i]);
        }
        mood.put("2026-11-05", 4);
        mood.put("2026-11-02", 1);
        mood.put("2026-10-30", 2);
        mood.put("2026-10-27", 0);
        mood.put("2026-10-20", 3);
        mood.put("2026-10-12", 1);
        mood.put("2026-10-05", 2);
        mood.put("2026-09-28", 3);
        mood.put("2026-09-24", 0);

        float[] w = MoodCurve.weekly(days, mood, "2026-11-05", 6);
        eq("curve.len", w.length, 6);
        ok("curve.lastHasData", !Float.isNaN(w[5]));
        ok("curve.lastIsHot", Math.abs(w[5] - 0.85f) < 0.001f);
        ok("curve.normalized", w[5] <= 1f && w[5] >= 0f);
        float outOfRange = 0;
        for (float v : w) {
            if (!Float.isNaN(v) && (v < 0f || v > 1f)) outOfRange++;
        }
        eq("curve.range", outOfRange, 0f);

        float[] empty = MoodCurve.weekly(new ArrayList<String>(), new HashMap<String, Integer>(),
                "2026-11-05", 6);
        boolean allNaN = true;
        for (float v : empty) if (!Float.isNaN(v)) allNaN = false;
        ok("curve.emptyAllNaN", allNaN);
        eq("curve.caption.empty", MoodCurve.caption(empty), "这六周，还没有颜色");
        eq("curve.caption.hot", MoodCurve.caption(new float[]{1f, 1f, 1f}), "这六周，偏炽烈");
        eq("curve.caption.mid", MoodCurve.caption(new float[]{0.5f, 0.5f}), "这六周，平稳");
        eq("curve.caption.low", MoodCurve.caption(new float[]{0.1f, 0.2f}), "这六周，偏低落");
        eq("curve.weeksZero", MoodCurve.weekly(days, mood, "2026-11-05", 0).length, 0);

        int[][] segs = MoodCurve.segments(new float[]{Float.NaN, 0.2f, 0.3f, Float.NaN, 0.6f});
        eq("curve.segCount", segs.length, 2);
        eq("curve.seg1", segs[0][0] + "-" + segs[0][1], "1-2");
        eq("curve.seg2", segs[1][0] + "-" + segs[1][1], "4-4");
        eq("curve.segsNull", MoodCurve.segments(null).length, 0);
        eq("curve.weeksAgoLabel", MoodCurve.weeksAgoLabel("2026-11-05", 6), "10/1");

        // ---------- Replay ----------
        eq("replay.duration0", Replay.durationOf(0), 0L);
        eq("replay.durationMin", Replay.durationOf(3), Replay.MIN_MS);
        eq("replay.durationMax", Replay.durationOf(500), Replay.MAX_MS);
        eq("replay.durationMid", Replay.durationOf(40), 4400L);
        eq("replay.indexStart", Replay.indexAt(0f, 10), 0);
        eq("replay.indexEnd", Replay.indexAt(1f, 10), 9);
        eq("replay.indexHalf", Replay.indexAt(0.5f, 10), 5);
        eq("replay.indexNone", Replay.indexAt(0.5f, 0), -1);
        eq("replay.progressClampHigh", Replay.progressOf(99999, 1000), 1f);
        eq("replay.progressClampLow", Replay.progressOf(-5, 1000), 0f);
        eq("replay.progressZeroDur", Replay.progressOf(10, 0), 1f);
        eq("replay.ease0", Replay.ease(0f), 0f);
        eq("replay.ease1", Replay.ease(1f), 1f);
        ok("replay.easeMonotonic", Replay.ease(0.3f) < Replay.ease(0.6f));
        eq("replay.seekMid", Replay.seek(50, 0, 100), 0.5f);
        eq("replay.seekOut", Replay.seek(-20, 0, 100), 0f);
        eq("replay.seekZeroWidth", Replay.seek(50, 10, 10), 0f);
        eq("replay.caption", Replay.caption("2026-04-08"), "2026 · 04 · 08");
        eq("replay.captionShort", Replay.caption("2026"), "");

        // ---------- WidgetState ----------
        String raw = "2026-11-05:4:今天:小满,2026-11-04:1,2026-11-04:2,bad,2026-11-03:0";
        eq("widget.total", WidgetState.total(raw), 3);
        eq("widget.totalEmpty", WidgetState.total(""), 0);
        eq("widget.totalNull", WidgetState.total(null), 0);
        ok("widget.litToday", WidgetState.litToday(raw, "2026-11-05"));
        ok("widget.notLit", !WidgetState.litToday(raw, "2026-11-06"));
        eq("widget.line1.lit", WidgetState.line1(raw, "2026-11-05", 3), "第 3 颗星");
        eq("widget.line1.unlit", WidgetState.line1(raw, "2026-11-06", 3), "今天还没点亮");
        eq("widget.line2.unlit", WidgetState.line2(raw, "2026-11-06", 3), "轻触，放一颗进夜空");
        eq("widget.line2.first", WidgetState.line2("2026-11-05:0", "2026-11-05", 1), "这是你的第一颗");
        eq("widget.line2.many", WidgetState.line2(raw, "2026-11-05", 3), "已经认真过了 3 天");
        eq("widget.hint.lit", WidgetState.hint(raw, "2026-11-05"), "拾光 · 今日已亮");
        eq("widget.hint.unlit", WidgetState.hint(raw, "2026-11-06"), "拾光 · 待点亮");

        System.out.println("---- v4.0 pure logic: " + pass + " passed, " + fail + " failed ----");
        if (fail > 0) System.exit(1);
    }
}
