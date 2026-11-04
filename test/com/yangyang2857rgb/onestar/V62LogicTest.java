package com.yangyang2857rgb.onestar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 拾光 v6.2 补记标记 / 撤销 相关纯逻辑单测：不依赖 android，直接 JVM 运行。 */
public class V62LogicTest {

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
        // ---------- DaysCodec 编码：补记标记写在第 5 段 ----------
        List<String> days = new ArrayList<String>();
        days.add("2026-11-06");
        days.add("2026-11-01");
        days.add("2026-10-20");
        Map<String, Integer> mood = new HashMap<String, Integer>();
        mood.put("2026-11-06", 1);
        mood.put("2026-11-01", 2);
        mood.put("2026-10-20", 0);
        Map<String, String> note = new HashMap<String, String>();
        note.put("2026-11-01", "风很轻");
        Map<String, String> name = new HashMap<String, String>();
        name.put("2026-10-20", "小满");
        Set<String> back = new HashSet<String>();
        back.add("2026-11-01");
        back.add("2026-10-20");

        String raw = DaysCodec.encode(days, mood, note, name, back);
        eq("encode 无备注无星名带补记", raw.split(",")[0], "2026-11-06:1");
        eq("encode 有备注带补记", raw.split(",")[1], "2026-11-01:2:风很轻::1");
        eq("encode 空备注有星名带补记", raw.split(",")[2], "2026-10-20:0::小满:1");

        // ---------- 往返一致 ----------
        List<String> d2 = new ArrayList<String>();
        Map<String, Integer> m2 = new HashMap<String, Integer>();
        Map<String, String> n2 = new HashMap<String, String>();
        Map<String, String> nm2 = new HashMap<String, String>();
        Set<String> b2 = new HashSet<String>();
        DaysCodec.parse(raw, d2, m2, n2, nm2, b2);
        eq("roundtrip days", d2, days);
        eq("roundtrip mood", m2.get("2026-11-01"), 2);
        eq("roundtrip note", n2.get("2026-11-01"), "风很轻");
        eq("roundtrip name", nm2.get("2026-10-20"), "小满");
        eq("roundtrip backfill size", b2.size(), 2);
        eq("roundtrip 含补记日", b2.contains("2026-11-01"), true);
        eq("roundtrip 当天不算补记", b2.contains("2026-11-06"), false);
        eq("roundtrip encode 稳定", DaysCodec.encode(d2, m2, n2, nm2, b2), raw);

        // ---------- 旧格式兼容：没有第 5 段就一律不算补记 ----------
        String legacy = "2021-05-04,2022-06-01:2,2023-07-09:1:旧备注";
        List<String> d3 = new ArrayList<String>();
        Map<String, Integer> m3 = new HashMap<String, Integer>();
        Map<String, String> n3 = new HashMap<String, String>();
        Map<String, String> nm3 = new HashMap<String, String>();
        Set<String> b3 = new HashSet<String>();
        DaysCodec.parse(legacy, d3, m3, n3, nm3, b3);
        eq("legacy 三天都读到", d3.size(), 3);
        eq("legacy 纯日期", d3.contains("2021-05-04"), true);
        eq("legacy 旧备注", n3.get("2023-07-09"), "旧备注");
        eq("legacy 无补记", b3.size(), 0);
        eq("legacy 无补记时不写第 5 段", DaysCodec.encode(d3, m3, n3, nm3, null), "2021-05-04:0,2022-06-01:2,2023-07-09:1:旧备注");

        // ---------- 备注里的冒号逗号不会破坏结构 ----------
        Map<String, String> n4 = new HashMap<String, String>();
        n4.put("2026-11-06", "12:30 吃了,一碗面");
        Set<String> b4 = new HashSet<String>();
        String raw4 = DaysCodec.encode(days.subList(0, 1), mood, n4, nm2, b4);
        List<String> d4 = new ArrayList<String>();
        Map<String, Integer> m4 = new HashMap<String, Integer>();
        Map<String, String> n5 = new HashMap<String, String>();
        Map<String, String> nm5 = new HashMap<String, String>();
        Set<String> b5 = new HashSet<String>();
        DaysCodec.parse(raw4, d4, m4, n5, nm5, b5);
        eq("转义后仍是一条", d4.size(), 1);
        eq("转义往返", n5.get("2026-11-06"), "12:30 吃了,一碗面");
        eq("转义后不误判补记", b5.size(), 0);

        // ---------- 补记 / 撤销 的交互辅助逻辑 ----------
        eq("补记文案含日期", DayEdit.backfillText("2026-11-01").contains("11 月 1 日"), true);
        eq("补记文案区分于当场点亮", DayEdit.backfillText("2026-11-01").equals(DayEdit.addedText("2026-11-01")), false);
        eq("撤销提示沿用 removedText", DayEdit.removedText("2026-11-01"), "抹掉了 11 月 1 日 那颗星");
        eq("今天也能补记（选色环同样适用）", DayEdit.canBackfill("2026-11-06", "2026-11-06"), true);
        eq("昨天能补记", DayEdit.canBackfill("2026-11-05", "2026-11-06"), true);
        eq("明天不能补记", DayEdit.canBackfill("2026-11-07", "2026-11-06"), false);
        eq("超五年不能补记", DayEdit.canBackfill("2020-01-01", "2026-11-06"), false);

        System.out.println("V62LogicTest: pass=" + pass + " fail=" + fail);
        if (fail > 0) System.exit(1);
    }
}
