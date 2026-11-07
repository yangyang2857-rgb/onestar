package com.yangyang2857rgb.onestar;

/**
 * 拾光 v4.0「星名」：给某一颗星起名字。
 * 纯逻辑，不依赖 android，可直接在桌面 JVM 上单测。
 */
final class StarName {

    /** 星名长度上限（按字符计，一个汉字算一个）。 */
    static final int MAX = 8;

    private StarName() {
    }

    /** 规整用户输入：去换行、去首尾空白、合并空白、截断到上限。 */
    static String clean(String raw) {
        if (raw == null) return "";
        String s = raw.replace("\r", " ").replace("\n", " ").replace("\t", " ").trim();
        StringBuilder sb = new StringBuilder(s.length());
        boolean lastSpace = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            boolean sp = ch == ' ';
            if (sp) {
                if (!lastSpace && sb.length() > 0) sb.append(' ');
            } else {
                sb.append(ch);
            }
            lastSpace = sp;
        }
        s = sb.toString().trim();
        if (s.codePointCount(0, s.length()) > MAX) {
            s = s.substring(0, s.offsetByCodePoints(0, MAX));
        }
        return s;
    }

    /**
     * 没有起名时的显示名：按第几颗 + 当天心情词（如「第 7 颗 · 絮语」）。
     * index 从 1 开始；越界或非法时退化为「某颗星」。
     */
    static String fallback(int index, int moodIdx) {
        String word = "";
        if (moodIdx >= 0 && moodIdx < Constellation.MOOD_WORD.length) {
            word = Constellation.moodWord(moodIdx);
        }
        if (index <= 0) {
            String any = I18n.t("某颗星", "A star");
            return word.length() == 0 ? any : any + " · " + word;
        }
        String ord = I18n.nth(index);
        return word.length() == 0 ? ord : ord + " · " + word;
    }

    /** 卡片上显示的名字：有自定义名就显示自定义名，否则用默认名。 */
    static String display(String custom, int index, int moodIdx) {
        String c = clean(custom);
        return c.length() > 0 ? c : fallback(index, moodIdx);
    }
}
