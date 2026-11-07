package com.yangyang2857rgb.onestar;

import java.util.Locale;

/**
 * 拾光 v6.5 双语层（纯 Java，无 android 依赖，可桌面 JVM 单测）。
 * 规则：中文是原始文案，英文是对照；系统语言只要不是中文（zh*），一律显示英文。
 * 之所以不用 strings.xml：本 App 绝大多数文字是 Canvas 手绘的拼接句（"第 3 颗 · 絮语"），
 * 放进资源表反而要拆成一堆占位符，这里用「中文原文 + 英文对照」两个常量最省事也最好测。
 * 进程存活期间切换系统语言不会重新加载类，所以每次回到前台都要调一次 {@link #refresh()}。
 */
public final class I18n {

    private static final String[] MONTH_ZH = {
            "一月", "二月", "三月", "四月", "五月", "六月",
            "七月", "八月", "九月", "十月", "十一月", "十二月"
    };

    private static final String[] MONTH_EN = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
    };

    private static final String[] MONTH_EN_SHORT = {
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    };

    private static volatile boolean en = detect();

    /** 测试与调试用的语言锁：null 表示跟随系统。 */
    private static volatile Boolean forced = null;

    private I18n() {
    }

    private static boolean detect() {
        try {
            String tag = Locale.getDefault().toString();
            if (tag == null) return false;
            return !tag.toLowerCase(Locale.ROOT).startsWith("zh");
        } catch (Throwable t) {
            return false;
        }
    }

    /** 系统语言可能变，进程却还活着；每次回到前台重新判定一次。 */
    public static void refresh() {
        Boolean f = forced;
        en = f == null ? detect() : f.booleanValue();
    }

    /** 仅测试用：true 强制英文、false 强制中文、null 恢复跟随系统。 */
    static void force(Boolean english) {
        forced = english;
        refresh();
    }

    public static boolean en() {
        return en;
    }

    /** 中文 / 英文二选一。 */
    public static String t(String zh, String enText) {
        return en ? enText : zh;
    }

    /** 带占位符的句子。中英语序不同，所以格式串各给一份。 */
    public static String f(String zhFmt, String enFmt, Object... args) {
        return String.format(Locale.ROOT, en ? enFmt : zhFmt, args);
    }

    /** 1..12 -> 一月 / January */
    public static String month(int month1to12) {
        if (month1to12 < 1 || month1to12 > 12) return "";
        return en ? MONTH_EN[month1to12 - 1] : MONTH_ZH[month1to12 - 1];
    }

    /** 1..12 -> 一月 / Jan（窄位置用） */
    public static String monthShort(int month1to12) {
        if (month1to12 < 1 || month1to12 > 12) return "";
        return en ? MONTH_EN_SHORT[month1to12 - 1] : MONTH_ZH[month1to12 - 1];
    }

    /** N 天 / N days */
    public static String days(int n) {
        return f("%d 天", "%d days", n);
    }

    /** N 颗星 / N stars */
    public static String stars(int n) {
        return f("%d 颗星", "%d stars", n);
    }

    /** 第 N 颗 / #N */
    public static String nth(int n) {
        return f("第 %d 颗", "#%d", n);
    }

    /** 日期：中文「11 月 4 日」，英文「Nov 4」。 */
    public static String date(int month1to12, int day) {
        return en ? monthShort(month1to12) + " " + day : month1to12 + " 月 " + day + " 日";
    }

    private static final String[] WEEKDAY_ZH = {
            "周日", "周一", "周二", "周三", "周四", "周五", "周六"
    };

    private static final String[] WEEKDAY_EN = {
            "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"
    };

    private static final String[] WEEKDAY_HEAD_ZH = {
            "日", "一", "二", "三", "四", "五", "六"
    };

    private static final String[] WEEKDAY_HEAD_EN = {
            "S", "M", "T", "W", "T", "F", "S"
    };

    /** 0=周日（与 Calendar.DAY_OF_WEEK - 1 对齐）→ 周日 / Sun */
    public static String weekday(int idx) {
        int i = ((idx % 7) + 7) % 7;
        return en ? WEEKDAY_EN[i] : WEEKDAY_ZH[i];
    }

    /** 日历表头：日 / S */
    public static String weekdayHead(int idx) {
        int i = ((idx % 7) + 7) % 7;
        return en ? WEEKDAY_HEAD_EN[i] : WEEKDAY_HEAD_ZH[i];
    }
}
