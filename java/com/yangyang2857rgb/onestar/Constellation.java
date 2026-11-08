package com.yangyang2857rgb.onestar;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 拾光 v2.0 星座引擎（纯 Java，无 android 依赖，可桌面 JVM 单测）。
 *
 * 规则：同一个月内点亮的所有星，按日期顺序连成一条星座；
 * 星座名 = 月份 + 主导心情词 + 星数词 + "座"。同名星座每月唯一且确定。
 */
final class Constellation {

    /** 心情枚举：0 静谧 1 雀跃 2 温柔 3 低落 4 炽烈 */
    static final int MOOD_N = 5;

    static final String[] MOOD_NAME = {"静谧", "雀跃", "温柔", "低落", "炽烈"};
    static final String[] MOOD_WORD = {"长夜", "跃光", "絮语", "潮汐", "余烬"};
    static final String[] MOOD_NAME_EN = {"Still", "Bright", "Soft", "Low", "Fierce"};
    static final String[] MOOD_WORD_EN = {"Nightfall", "Leap", "Whisper", "Tide", "Ember"};

    /** 星色（ARGB）。5 种心情各自的星点色与光晕色。 */
    static final int[] MOOD_COLOR = {
            0xFFCFE0FF, 0xFFFFE7A8, 0xFFFFC2D4, 0xFFA9B4D6, 0xFFFF9E7A
    };
    static final int[] MOOD_GLOW = {
            0xFF9FBEFF, 0xFFFFD98A, 0xFFFFA8C4, 0xFF98A6CE, 0xFFFF8A62
    };

    private static final String[] MONTH_CN = {
            "一月", "二月", "三月", "四月", "五月", "六月",
            "七月", "八月", "九月", "十月", "十一月", "十二月"
    };

    private static final String[] NUM_CN = {"", "一", "二", "三", "四", "五", "六", "七", "八", "九"};

    private Constellation() {
    }

    /** 心情名：静谧 / Still */
    static String moodName(int i) {
        if (i < 0 || i >= MOOD_N) i = 0;
        return I18n.t(MOOD_NAME[i], MOOD_NAME_EN[i]);
    }

    /** 心情词（星座名后缀）：长夜 / Nightfall */
    static String moodWord(int i) {
        if (i < 0 || i >= MOOD_N) i = 0;
        return I18n.t(MOOD_WORD[i], MOOD_WORD_EN[i]);
    }

    /** "2026-11-04" -> "2026-11" */
    static String monthKey(String date) {
        if (date == null || date.length() < 7) return "";
        return date.substring(0, 7);
    }

    /** "2026-11-04" -> 11（1..12），解析失败返回 0 */
    static int monthOf(String date) {
        try {
            return Integer.parseInt(date.substring(5, 7));
        } catch (Exception e) {
            return 0;
        }
    }

    /** "2026-11" -> "十一月" */
    static String monthName(String monthKey) {
        return I18n.month(monthOf(monthKey + "-01"));
    }

    /** 某月内的日期列表（保持入参顺序，调用方已排序） */
    static List<String> inMonth(List<String> days, String monthKey) {
        List<String> out = new ArrayList<String>();
        for (String d : days) {
            if (monthKey.equals(monthKey(d))) out.add(d);
        }
        return out;
    }

    /** 该组日期中出现最多的心情；票数相同取序号小的（静谧优先）。 */
    static int dominantMood(List<String> days, Map<String, Integer> mood) {
        int[] cnt = new int[MOOD_N];
        for (String d : days) {
            Integer m = mood.get(d);
            int v = m == null ? 0 : m;
            if (v >= 0 && v < MOOD_N) cnt[v]++;
        }
        int best = 0;
        for (int i = 1; i < MOOD_N; i++) {
            if (cnt[i] > cnt[best]) best = i;
        }
        return best;
    }

    /** 星数 -> 中文数词："二" "三" … 九以上用"群" */
    static String numCn(int n) {
        if (n <= 0) return "";
        int lg = I18n.lang();
        if (lg != I18n.ZH && lg != I18n.ZH_HANT) {
            return n >= 10 ? I18n.t("群", "Many") : I18n.num(n);
        }
        if (n < NUM_CN.length) return NUM_CN[n];
        return "群";
    }

    /**
     * 星座命名。
     * n=2："十一月 · 双星长夜"；n=3："十一月 · 三星长夜"；n=12："十一月 · 群星长夜"
     */
    static String constellationName(String monthKey, List<String> monthDays, Map<String, Integer> mood) {
        String mn = monthName(monthKey);
        if (mn.length() == 0) return "";
        int n = monthDays.size();
        if (n == 0) return "";
        int dm = dominantMood(monthDays, mood);
        return I18n.f("%1$s · %2$s%3$s", "%1$s · %2$s%3$s", mn, sizeWord(n), moodWord(dm));
    }

    /**
     * 星数词：孤星 / 双星 / 三星 / 群星。
     * 各语言自行决定是否带尾随空格（中文、日文不带，其余带），拼接模板统一。
     */
    static String sizeWord(int n) {
        if (n <= 0) return "";
        if (n == 1) return I18n.t("孤星", "Lone ");
        if (n == 2) return I18n.t("双星", "Twin ");
        if (n >= 10) return I18n.t("群星", "Many-Star ");
        if (I18n.lang() == I18n.ZH || I18n.lang() == I18n.ZH_HANT) {
            return numCn(n) + "星";
        }
        return I18n.f("%d 星", "%1$d-Star ", Integer.valueOf(n));
    }

    /** 该年 12 个月的点亮数（长度 12，索引 0=1 月） */
    static int[] monthlyCounts(List<String> days, int year) {
        int[] out = new int[12];
        for (String d : days) {
            int y = yearOf(d);
            if (y != year) continue;
            int m = monthOf(d);
            if (m >= 1 && m <= 12) out[m - 1]++;
        }
        return out;
    }

    /** 某年每个月的颜色（用于星盘色带），无星月份为 0 */
    static int[] monthlyColors(List<String> days, Map<String, Integer> mood, int year) {
        int[] out = new int[12];
        for (int m = 1; m <= 12; m++) {
            String key = String.format("%04d-%02d", year, m);
            List<String> ms = inMonth(days, key);
            if (ms.isEmpty()) continue;
            out[m - 1] = MOOD_COLOR[dominantMood(ms, mood)];
        }
        return out;
    }

    static int yearOf(String date) {
        try {
            return Integer.parseInt(date.substring(0, 4));
        } catch (Exception e) {
            return 0;
        }
    }

    /** 数据里出现最多的年份（空数据返回当前年由调用方决定） */
    static int busiestYear(List<String> days, int fallback) {
        Map<Integer, Integer> cnt = new HashMap<Integer, Integer>();
        for (String d : days) {
            int y = yearOf(d);
            if (y < 1970) continue;
            Integer c = cnt.get(y);
            cnt.put(y, c == null ? 1 : c + 1);
        }
        int bestY = fallback, bestC = -1;
        for (Map.Entry<Integer, Integer> e : cnt.entrySet()) {
            if (e.getValue() > bestC || (e.getValue() == bestC && e.getKey() > bestY)) {
                bestC = e.getValue();
                bestY = e.getKey();
            }
        }
        return bestY;
    }

    /** 星语：一句由数据决定的话，同一数据永远同一句。 */
    static String verse(List<String> days, Map<String, Integer> mood, int total, int streak, int spanDays) {
        if (total == 0) {
            return I18n.t("夜空还没开始，第一颗星等你来点",
                    "The sky is still empty. Your first star is waiting.");
        }
        int dm = dominantMood(days, mood);
        if (streak >= 14) {
            return I18n.f("连续 %d 天，你的夜空已经不缺光",
                    "%d days in a row — your sky is never short of light.", streak);
        }
        if (streak >= 7) {
            return I18n.f("连着 %d 天，银河都记住你了",
                    "%d days running. Even the galaxy remembers you.", streak);
        }
        if (total == 1) {
            return I18n.t("第一颗星落下了，故事从这里开始",
                    "The first star has landed. The story starts here.");
        }
        if (spanDays > 0 && total >= spanDays * 0.8f) {
            return I18n.f("陪伴 %d 天，几乎每一天都有光",
                    "%d days together, and almost every one of them lit.", spanDays);
        }
        switch (dm) {
            case 0:
                return I18n.t("多数夜晚是静的，你把它留给了自己",
                        "Most nights were quiet, and you kept them for yourself.");
            case 1:
                return I18n.t("你的天空亮得偏暖，多半是好日子",
                        "Your sky runs warm, which usually means good days.");
            case 2:
                return I18n.t("温柔的颜色最多，像有人在替你收着心事",
                        "Soft colors dominate, as if someone is holding your worries.");
            case 3:
                return I18n.t("低落也有自己的光，它只是暗一点",
                        "Low days have their own light. It is just dimmer.");
            default:
                return I18n.t("炽烈的那几天，星星都烧得更亮",
                        "On the fierce days, the stars burned brighter.");
        }
    }

    /** 星座连线：返回该月星点在 0..1 归一化坐标中的折线节点数（用于预览与调试） */
    static int nodesOf(List<String> days, String monthKey) {
        return inMonth(days, monthKey).size();
    }

    /** 今天是否已点亮（纯逻辑，便于单测） */
    static boolean contains(List<String> days, String date) {
        return days != null && days.contains(date);
    }

    /** 连续天数（从 today 或昨天回溯） */
    static int streakOf(List<String> days, String today) {
        if (days == null || days.isEmpty()) return 0;
        Calendar c = Calendar.getInstance();
        String cur = today;
        if (!days.contains(cur)) {
            c.setTimeInMillis(parseDate(today));
            c.add(Calendar.DAY_OF_YEAR, -1);
            cur = fmt(c);
            if (!days.contains(cur)) return 0;
        }
        int n = 0;
        while (days.contains(cur)) {
            n++;
            c.setTimeInMillis(parseDate(cur));
            c.add(Calendar.DAY_OF_YEAR, -1);
            cur = fmt(c);
        }
        return n;
    }

    static long parseDate(String s) {
        try {
            int y = Integer.parseInt(s.substring(0, 4));
            int m = Integer.parseInt(s.substring(5, 7));
            int d = Integer.parseInt(s.substring(8, 10));
            Calendar c = Calendar.getInstance();
            c.set(y, m - 1, d, 12, 0, 0);
            c.set(Calendar.MILLISECOND, 0);
            return c.getTimeInMillis();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private static String fmt(Calendar c) {
        return String.format("%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }
}
