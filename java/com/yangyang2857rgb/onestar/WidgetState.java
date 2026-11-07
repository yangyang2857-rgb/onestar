package com.yangyang2857rgb.onestar;

/**
 * 拾光 v4.0「桌面小组件」的状态计算：从存储原始串里读出总数与今天是否点亮。
 * 纯逻辑，不依赖 android，可直接在桌面 JVM 上单测。
 */
final class WidgetState {

    private WidgetState() {
    }

    private static boolean isDate(String s) {
        if (s == null || s.length() != 10) return false;
        if (s.charAt(4) != '-' || s.charAt(7) != '-') return false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (i == 4 || i == 7) continue;
            if (ch < '0' || ch > '9') return false;
        }
        return true;
    }

    /** 已点亮天数（同一日期只算一次）。 */
    static int total(String raw) {
        if (raw == null || raw.trim().length() == 0) return 0;
        java.util.List<String> seen = new java.util.ArrayList<String>();
        for (String seg : raw.split(",")) {
            String d = seg.trim().split(":", -1)[0].trim();
            if (!isDate(d) || seen.contains(d)) continue;
            seen.add(d);
        }
        return seen.size();
    }

    /** 今天是否已经点亮。 */
    static boolean litToday(String raw, String today) {
        if (raw == null || today == null) return false;
        for (String seg : raw.split(",")) {
            String d = seg.trim().split(":", -1)[0].trim();
            if (today.equals(d)) return true;
        }
        return false;
    }

    /** 小组件第一行文案。 */
    static String line1(String raw, String today, int total) {
        return litToday(raw, today)
                ? I18n.f("第 %d 颗星", "Star #%d", total)
                : I18n.t("今天还没点亮", "Not lit yet");
    }

    /** 小组件第二行文案。 */
    static String line2(String raw, String today, int total) {
        if (!litToday(raw, today)) {
            return I18n.t("轻触，放一颗进夜空", "Tap to put one in the sky");
        }
        if (total <= 1) return I18n.t("这是你的第一颗", "Your very first one");
        return I18n.f("已经认真过了 %d 天", "%d days, all lived", total);
    }

    /** 小组件角落的小字。 */
    static String hint(String raw, String today) {
        boolean lit = litToday(raw, today);
        return I18n.t("拾光 · ", "OneStar · ")
                + (lit ? I18n.t("今日已亮", "Lit today") : I18n.t("待点亮", "Not yet"));
    }
}
