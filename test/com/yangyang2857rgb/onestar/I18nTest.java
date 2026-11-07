package com.yangyang2857rgb.onestar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * v6.5 双语层单测：不依赖 android.*，也不需要模拟器。
 * 重点验三件事：
 * 1. 英文模式下逻辑类产出的文案里不能再有汉字（否则外语机上就是半中半英）；
 * 2. 关键格式正确（November · Twin Whisper / #7 · Whisper / Nov 4）；
 * 3. 切回中文后与原行为完全一致。
 */
public final class I18nTest {

    private static int pass = 0;
    private static int fail = 0;

    private I18nTest() {
    }

    private static void check(String name, boolean ok, String got, String want) {
        if (ok) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL: " + name + "  got=" + got + " want=" + want);
        }
    }

    private static void eq(String name, String got, String want) {
        check(name, got != null && got.equals(want), String.valueOf(got), want);
    }

    private static boolean hasCjk(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x4e00 && c <= 0x9fff) return true;
        }
        return false;
    }

    private static void noCjk(String name, String s) {
        check(name, s != null && !hasCjk(s), String.valueOf(s), "纯英文（无汉字）");
    }

    private static List<String> list(String... days) {
        List<String> out = new ArrayList<String>();
        for (String d : days) out.add(d);
        return out;
    }

    public static void main(String[] args) {
        List<String> days = list("2026-11-02", "2026-11-05");
        Map<String, Integer> mood = new HashMap<String, Integer>();
        mood.put("2026-11-02", 2);
        mood.put("2026-11-05", 2);

        // ---------- 中文（默认） ----------
        I18n.force(Boolean.FALSE);
        eq("zh.mood", Constellation.moodName(2), "温柔");
        eq("zh.month", Constellation.monthName("2026-11"), "十一月");
        eq("zh.constellation", Constellation.constellationName("2026-11", days, mood), "十一月 · 双星絮语");
        eq("zh.nth", I18n.nth(7), "第 7 颗");
        eq("zh.days", I18n.days(3), "3 天");
    eq("zh.date", I18n.date(11, 4), "11 月 4 日");
    eq("zh.weekday", I18n.weekday(0), "周日");
    eq("zh.weekdayHead", I18n.weekdayHead(3), "三");

        // ---------- 英文 ----------
        I18n.force(Boolean.TRUE);
        eq("en.mood", Constellation.moodName(2), "Soft");
        eq("en.moodWord", Constellation.moodWord(2), "Whisper");
        eq("en.month", Constellation.monthName("2026-11"), "November");
        eq("en.monthShort", I18n.monthShort(11), "Nov");
    eq("en.date", I18n.date(11, 4), "Nov 4");
    eq("en.weekday", I18n.weekday(0), "Sun");
    eq("en.weekdayHead", I18n.weekdayHead(3), "W");
    for (int i = 0; i < 7; i++) noCjk("en.weekday." + i, I18n.weekday(i));
        eq("en.constellation.2", Constellation.constellationName("2026-11", days, mood),
                "November · Twin Whisper");
        eq("en.constellation.one", Constellation.constellationName("2026-11",
                list("2026-11-02"), mood), "November · Lone Whisper");
        eq("en.verse.empty", Constellation.verse(list(), mood, 0, 0, 0),
                "The sky is still empty. Your first star is waiting.");
        noCjk("en.verse.streak", Constellation.verse(days, mood, 20, 14, 30));
        noCjk("en.verse.first", Constellation.verse(days, mood, 1, 0, 0));

        eq("en.pretty", DayEdit.pretty("2026-11-04"), "Nov 4");
        noCjk("en.addedText", DayEdit.addedText("2026-11-04"));
        noCjk("en.backfillText", DayEdit.backfillText("2026-11-04"));
        noCjk("en.recoloredText", DayEdit.recoloredText("2026-11-04", "Soft"));
        noCjk("en.removedText", DayEdit.removedText("2026-11-04"));
        noCjk("en.deleteLabel.on", DayEdit.deleteLabel(true));
        noCjk("en.deleteLabel.off", DayEdit.deleteLabel(false));
        noCjk("en.backfillHint", DayEdit.backfillHint());

        eq("en.starName.fallback", StarName.display(null, 7, 2), "#7 · Whisper");
        eq("en.starName.any", StarName.display(null, 0, 2), "A star · Whisper");
        eq("en.starName.custom", StarName.display("  My star  ", 7, 2), "My star");

        eq("en.widget.line1", WidgetState.line1("2026-11-04:2", "2026-11-04", 3), "Star #3");
        eq("en.widget.line1.off", WidgetState.line1("2026-11-03:2", "2026-11-04", 3), "Not lit yet");
        eq("en.widget.line2.first", WidgetState.line2("2026-11-04:2", "2026-11-04", 1), "Your very first one");
        noCjk("en.widget.hint", WidgetState.hint("2026-11-04:2", "2026-11-04"));
        eq("en.widget.hint.lit", WidgetState.hint("2026-11-04:2", "2026-11-04"), "OneStar · Lit today");

        noCjk("en.curve", MoodCurve.caption(new float[]{0.9f, 0.95f}));
        noCjk("en.compat.note", CompatTable.pageNote(16384, true));
        noCjk("en.compat.mem", CompatTable.memNote(true, 2));
        eq("en.melody.month", Melody.monthName(10), "November");

        for (int i = 0; i < Constellation.MOOD_N; i++) {
            noCjk("en.mood." + i, Constellation.moodName(i));
            noCjk("en.moodWord." + i, Constellation.moodWord(i));
        }
        for (int m = 1; m <= 12; m++) {
            noCjk("en.month." + m, I18n.month(m));
            noCjk("en.monthShort." + m, I18n.monthShort(m));
        }

        // ---------- 切回中文 ----------
        I18n.force(Boolean.FALSE);
        eq("zh.back.mood", Constellation.moodName(2), "温柔");
        eq("zh.back.pretty", DayEdit.pretty("2026-11-04"), "11 月 4 日");
        eq("zh.back.widget", WidgetState.line1("2026-11-04:2", "2026-11-04", 3), "第 3 颗星");
        eq("zh.back.starName", StarName.display(null, 7, 2), "第 7 颗 · 絮语");
    eq("zh.back.date", I18n.date(1, 1), "1 月 1 日");

        I18n.force(null);
        check("force.reset", I18n.t("中", "EN") != null, "ok", "ok");

        System.out.println("I18nTest: pass=" + pass + " fail=" + fail);
        if (fail > 0) System.exit(1);
    }
}
