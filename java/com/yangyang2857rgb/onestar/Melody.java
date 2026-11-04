package com.yangyang2857rgb.onestar;

/** 拾光 v3.0 星月琴：把日期映射成半音，让一个月的星星连成一段可重放的旋律。 */
public final class Melody {

    /** 每月中文名（1-12） */
    public static final String[] MONTH_CN = {"一月", "二月", "三月", "四月", "五月", "六月",
            "七月", "八月", "九月", "十月", "十一月", "十二月"};

    private Melody() {
    }

    /** 同一天永远同一个音：日号 % 5 - 2，落在 -2..+2 个半音 */
    public static int semitone(String date) {
        if (date == null || date.length() < 10) return 0;
        try {
            int d = Integer.parseInt(date.substring(8, 10));
            return (d % 5) - 2;
        } catch (Exception e) {
            return 0;
        }
    }

    /** 同一个月的判定键，如 2026-10 */
    public static String monthOf(String date) {
        if (date == null || date.length() < 7) return "";
        return date.substring(0, 7);
    }

    /** 月份索引 0-11，非法返回 -1 */
    public static int monthIndex(String date) {
        if (date == null || date.length() < 7) return -1;
        try {
            int m = Integer.parseInt(date.substring(5, 7));
            return (m >= 1 && m <= 12) ? m - 1 : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    public static String monthName(int idx) {
        return (idx >= 0 && idx < 12) ? MONTH_CN[idx] : "";
    }
}
