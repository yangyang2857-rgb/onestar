package com.yangyang2857rgb.onestar;



import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 拾光 v2.0 星座引擎单测：不依赖 android，直接 JVM 运行 */
public class ConstellationTest {

    static int pass = 0, fail = 0;

    static void eq(String name, Object got, Object want) {
        boolean ok = String.valueOf(got).equals(String.valueOf(want));
        if (ok) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL  " + name + "   got=" + got + "  want=" + want);
        }
    }

    public static void main(String[] a) {
        // 1. 月份解析与中文名
        eq("monthKey", Constellation.monthKey("2026-11-04"), "2026-11");
        eq("monthName 11", Constellation.monthName("2026-11"), "十一月");
        eq("monthName 1", Constellation.monthName("2026-01"), "一月");
        eq("monthOf", "" + Constellation.monthOf("2026-07-15"), "7");

        // 2. 主导心情（平局取序号小）
        List<String> g = new ArrayList<String>();
        g.add("2026-11-01");
        g.add("2026-11-02");
        g.add("2026-11-03");
        Map<String, Integer> mood = new HashMap<String, Integer>();
        mood.put("2026-11-01", 1);
        mood.put("2026-11-02", 1);
        mood.put("2026-11-03", 3);
        eq("dominant 1(2票)", "" + Constellation.dominantMood(g, mood), "1");

        mood.put("2026-11-03", 0);
        eq("dominant 3(1票)", "" + Constellation.dominantMood(g, mood), "1");

        mood.clear();
        eq("dominant 无数据=静谧", "" + Constellation.dominantMood(g, mood), "0");

        // 3. 星数词
        eq("numCn 2", Constellation.numCn(2), "二");
        eq("numCn 9", Constellation.numCn(9), "九");
        eq("numCn 10", Constellation.numCn(10), "群");

        // 4. 星座命名
        List<String> two = new ArrayList<String>();
        two.add("2026-11-01");
        two.add("2026-11-05");
        Map<String, Integer> m2 = new HashMap<String, Integer>();
        m2.put("2026-11-01", 2);
        m2.put("2026-11-05", 2);
        eq("名称 2星温柔", Constellation.constellationName("2026-11", two, m2), "十一月 · 双星絮语");

        List<String> three = new ArrayList<String>();
        three.add("2026-03-01");
        three.add("2026-03-08");
        three.add("2026-03-15");
        Map<String, Integer> m3 = new HashMap<String, Integer>();
        for (String d : three) m3.put(d, 1);
        eq("名称 3星雀跃", Constellation.constellationName("2026-03", three, m3), "三月 · 三星跃光");

        // 12 星 -> 群星
        List<String> many = new ArrayList<String>();
        Map<String, Integer> m4 = new HashMap<String, Integer>();
        for (int i = 1; i <= 12; i++) {
            String d = String.format("2026-05-%02d", i);
            many.add(d);
            m4.put(d, 4);
        }
        eq("名称 12星炽烈", Constellation.constellationName("2026-05", many, m4), "五月 · 群星余烬");

        eq("空组命名", Constellation.constellationName("2026-09", new ArrayList<String>(), new HashMap<String, Integer>()), "");

        // 5. 月度统计
        List<String> days = new ArrayList<String>();
        days.add("2026-01-03");
        days.add("2026-01-09");
        days.add("2026-03-01");
        days.add("2025-12-31");
        int[] mc = Constellation.monthlyCounts(days, 2026);
        eq("2026 一月", "" + mc[0], "2");
        eq("2026 二月", "" + mc[1], "0");
        eq("2026 三月", "" + mc[2], "1");
        eq("2026 十二月", "" + mc[11], "0");

        int[] mc25 = Constellation.monthlyCounts(days, 2025);
        eq("2025 十二月", "" + mc25[11], "1");

        // 6. 月度颜色
        Map<String, Integer> mcolor = new HashMap<String, Integer>();
        mcolor.put("2026-01-03", 1);
        mcolor.put("2026-01-09", 1);
        mcolor.put("2026-03-01", 3);
        int[] cols = Constellation.monthlyColors(days, mcolor, 2026);
        eq("1月色=雀跃", Integer.toHexString(cols[0]), Integer.toHexString(Constellation.MOOD_COLOR[1]));
        eq("2月无色", "" + cols[1], "0");
        eq("3月色=低落", Integer.toHexString(cols[2]), Integer.toHexString(Constellation.MOOD_COLOR[3]));

        // 7. 连续天数
        List<String> s = new ArrayList<String>();
        s.add("2026-11-01");
        s.add("2026-11-02");
        s.add("2026-11-03");
        eq("streak 今天在", "" + Constellation.streakOf(s, "2026-11-03"), "3");
        eq("streak 昨天止", "" + Constellation.streakOf(s, "2026-11-04"), "3");
        eq("streak 断档", "" + Constellation.streakOf(s, "2026-11-06"), "0");

        List<String> s2 = new ArrayList<String>();
        s2.add("2026-10-31");
        s2.add("2026-11-01");
        eq("streak 跨月", "" + Constellation.streakOf(s2, "2026-11-01"), "2");

        // 8. 星语规则
        List<String> vd = new ArrayList<String>();
        Map<String, Integer> vm = new HashMap<String, Integer>();
        eq("verse 空", Constellation.verse(vd, vm, 0, 0, 0), "夜空还没开始，第一颗星等你来点");
        eq("verse 首颗", Constellation.verse(vd, vm, 1, 1, 1), "第一颗星落下了，故事从这里开始");
        eq("verse 连续14", Constellation.verse(vd, vm, 20, 14, 20), "连续 14 天，你的夜空已经不缺光");
        eq("verse 连续7", Constellation.verse(vd, vm, 10, 8, 10), "连着 8 天，银河都记住你了");

        // 9. 主导年份
        eq("busiestYear", "" + Constellation.busiestYear(days, 2026), "2026");

        System.out.println("\n通过 " + pass + " 项，失败 " + fail + " 项");
        if (fail > 0) System.exit(1);
    }
}
