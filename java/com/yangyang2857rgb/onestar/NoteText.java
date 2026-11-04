package com.yangyang2857rgb.onestar;

/**
 * 一句话备注的纯文本处理：长度规整与存储转义。
 * 不依赖 android，可直接在 JVM 上单测。
 * 转义只处理存储分隔符（% : ,），保证 "date:mood:note" 能被安全切分。
 */
final class NoteText {

    /** 备注长度上限（按字符计，一个汉字算一个）。 */
    static final int MAX = 40;

    private NoteText() {
    }

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

    static String encode(String note) {
        if (note == null || note.length() == 0) return "";
        StringBuilder sb = new StringBuilder(note.length() + 8);
        for (int i = 0; i < note.length(); i++) {
            char ch = note.charAt(i);
            switch (ch) {
                case '%': sb.append("%25"); break;
                case ':': sb.append("%3A"); break;
                case ',': sb.append("%2C"); break;
                default: sb.append(ch);
            }
        }
        return sb.toString();
    }

    static String decode(String s) {
        if (s == null || s.length() == 0) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '%' && i + 2 < s.length()) {
                try {
                    sb.append((char) Integer.parseInt(s.substring(i + 1, i + 3), 16));
                    i += 2;
                    continue;
                } catch (Exception ignored) {
                    // 非法转义原样保留
                }
            }
            sb.append(ch);
        }
        return sb.toString();
    }
}
