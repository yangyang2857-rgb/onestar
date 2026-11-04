package com.yangyang2857rgb.onestar;

/** 拾光 v6.1 星册编辑逻辑单测：不依赖 android，直接 JVM 运行。 */
public class DayEditTest {

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
        String today = "2026-11-06";

        // 格式
        eq("iso ok", DayEdit.isIso("2026-11-06"), true);
        eq("iso 闰日", DayEdit.isIso("2024-02-29"), true);
        eq("iso 非闰日", DayEdit.isIso("2025-02-29"), false);
        eq("iso 13 月", DayEdit.isIso("2026-13-01"), false);
        eq("iso 4 月 31", DayEdit.isIso("2026-04-31"), false);
        eq("iso null", DayEdit.isIso(null), false);
        eq("iso 空串", DayEdit.isIso(""), false);
        eq("iso 短", DayEdit.isIso("2026-1-1"), false);

        // 补记合法性
        eq("补昨天", DayEdit.canBackfill("2026-11-05", today), true);
        eq("补今天", DayEdit.canBackfill("2026-11-06", today), true);
        eq("不能补明天", DayEdit.canBackfill("2026-11-07", today), false);
        eq("五年内可补", DayEdit.canBackfill("2021-11-06", today), true);
        eq("超五年不可补", DayEdit.canBackfill("2021-11-05", today), false);
        eq("非法日期不可补", DayEdit.canBackfill("2026-02-30", today), false);
        eq("跨年可补", DayEdit.canBackfill("2025-12-31", today), true);

        // 文案
        eq("pretty", DayEdit.pretty("2026-11-04"), "11 月 4 日");
        eq("pretty 去零", DayEdit.pretty("2026-01-09"), "1 月 9 日");
        eq("pretty 非法原样", DayEdit.pretty("坏日期"), "坏日期");
        eq("added", DayEdit.addedText("2026-11-04"), "把 11 月 4 日 补回来了");
        eq("recolored", DayEdit.recoloredText("2026-11-04", "温柔"), "11 月 4 日 换成「温柔」");
        eq("removed", DayEdit.removedText("2026-11-04"), "抹掉了 11 月 4 日 那颗星");

        // 删除按钮两段式
        eq("未武装", DayEdit.deleteLabel(false), "抹掉这颗");
        eq("已武装", DayEdit.deleteLabel(true), "再点一次抹掉");

        System.out.println("DayEditTest: pass=" + pass + " fail=" + fail);
        if (fail > 0) System.exit(1);
    }
}
