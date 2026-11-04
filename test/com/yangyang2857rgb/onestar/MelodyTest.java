package com.yangyang2857rgb.onestar;

/** 拾光 v3.0 星月琴单测：不依赖 android，直接 JVM 运行 */
public class MelodyTest {

    static int pass = 0, fail = 0;

    static void eq(String name, Object got, Object want) {
        if (String.valueOf(got).equals(String.valueOf(want))) pass++;
        else { fail++; System.out.println("FAIL  " + name + "  got=" + got + "  want=" + want); }
    }

    public static void main(String[] a) {
        // 半音：日号 %5 -2，稳定落在 -2..+2
        eq("01 日", Melody.semitone("2026-11-01"), -1);
        eq("02 日", Melody.semitone("2026-11-02"), 0);
        eq("03 日", Melody.semitone("2026-11-03"), 1);
        eq("04 日", Melody.semitone("2026-11-04"), 2);
        eq("05 日", Melody.semitone("2026-11-05"), -2);
        eq("31 日", Melody.semitone("2026-12-31"), -1);
        eq("同日同音", Melody.semitone("2025-03-17"), Melody.semitone("2026-07-17"));
        eq("非法日期", Melody.semitone(""), 0);
        eq("null 日期", Melody.semitone(null), 0);

        // 单月同时覆盖 31 天，音域不超过 5 个音
        java.util.Set<Integer> set = new java.util.HashSet<Integer>();
        for (int d = 1; d <= 31; d++) set.add(Melody.semitone(String.format("2026-10-%02d", d)));
        eq("音域个数", set.size(), 5);

        eq("月份键", Melody.monthOf("2026-10-05"), "2026-10");
        eq("月份索引 10", Melody.monthIndex("2026-10-05"), 9);
        eq("月份索引 1", Melody.monthIndex("2026-01-31"), 0);
        eq("月份索引 12", Melody.monthIndex("2026-12-01"), 11);
        eq("月份索引非法", Melody.monthIndex("2026-13-01"), -1);
        eq("月份索引短串", Melody.monthIndex("2026"), -1);
        eq("中文名", Melody.monthName(10), "十一月");
        eq("中文名越界", Melody.monthName(12), "");

        // 一个月内的音序可复现（同数据必得同旋律）
        StringBuilder s1 = new StringBuilder(), s2 = new StringBuilder();
        for (int d = 1; d <= 30; d++) {
            String ds = String.format("2026-09-%02d", d);
            s1.append(Melody.semitone(ds)).append(',');
        }
        for (int d = 1; d <= 30; d++) {
            String ds = String.format("2026-09-%02d", d);
            s2.append(Melody.semitone(ds)).append(',');
        }
        eq("旋律可复现", s1.toString().equals(s2.toString()), true);
        eq("旋律长度", s1.toString().split(",").length, 30);

        System.out.println("MelodyTest  pass=" + pass + "  fail=" + fail);
        if (fail > 0) System.exit(1);
    }
}
