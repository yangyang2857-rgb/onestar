import com.yangyang2857rgb.onestar.I18n;

/**
 * 多语言文案表纯逻辑单测：不依赖 Android，javac + java 直接跑。
 *
 * 同包内的 I18nTest 只验「外语模式下各逻辑类不残留汉字」，本类负责文案表本身。
 *
 * 三类断言：
 * 1) 表完整性 —— 15 种语言、每行都有译文、占位符数量与中文原文对得上；
 * 2) 语言判定 —— detect/forceTag 覆盖zh-Hant/ja/ko/fr/de/es/es-419/ar/th/pt-BR/pt-PT/bo/ug；
 * 3) 输出正确 —— 非中日语言不得漏出汉字，日期/数字/引号按语言变形。
 */
public class I18nTableTest {
    static int pass = 0;
    static int fail = 0;

    static void eq(String name, Object got, Object want) {
        if (String.valueOf(got).equals(String.valueOf(want))) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + name + " got=" + got + " want=" + want);
        }
    }

    static void ok(String name, boolean cond, String detail) {
        if (cond) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + name + " " + detail);
        }
    }

    /** 汉字（不含全角标点）。 */
    static boolean hasHan(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u4e00' && c <= '\u9fff') return true;
        }
        return false;
    }

    /** 统计格式串里的 %s / %d（含 %1$s 这类索引写法）出现次数。 */
    static int placeholders(String s) {
        int n = 0;
        for (int i = 0; i < s.length() - 1; i++) {
            if (s.charAt(i) != '%') continue;
            int j = i + 1;
            while (j < s.length() && Character.isDigit(s.charAt(j))) j++;
            if (j < s.length() && s.charAt(j) == '$') j++;
            if (j < s.length() && (s.charAt(j) == 's' || s.charAt(j) == 'd')) n++;
        }
        return n;
    }

    public static void main(String[] a) {
        I18n.force(Boolean.TRUE);

        // ---- 1. 表完整性 ----
        int rows = I18n.rows();
        ok("rowsAtLeast180", rows >= 180, "rows=" + rows);
        eq("count15", I18n.COUNT, 15);
        eq("tags15", I18n.TAGS.length, I18n.COUNT);
        eq("names15", I18n.NATIVE_NAMES.length, I18n.COUNT);

        for (int l = 0; l < I18n.COUNT; l++) {
            String[] col = I18n.column(l);
            ok("columnLen#" + I18n.TAGS[l], col != null && col.length == rows,
                    col == null ? "null" : "len=" + col.length);
            if (col == null) continue;
            for (int i = 0; i < rows; i++) {
                String v = col[i];
                if (v == null || v.trim().length() == 0) {
                    ok("empty#" + I18n.TAGS[l] + "#" + i, false, "key=" + I18n.zhKey(i));
                    continue;
                }
                String zh = I18n.zhKey(i);
                if (zh == null) continue;
                int want = placeholders(zh);
                int got = placeholders(v);
                ok("ph#" + I18n.TAGS[l] + "#" + i, want == got,
                        "key=" + zh + " want=" + want + " got=" + got + " val=" + v);
            }
        }

        // ---- 2. 语言判定 ----
        String[][] tags = {
                {"zh", "zh"}, {"zh-CN", "zh"}, {"zh-Hans", "zh"},
                {"zh-Hant", "zh-Hant"}, {"zh-TW", "zh-Hant"}, {"zh-HK", "zh-Hant"},
                {"en", "en"}, {"en-US", "en"},
                {"ja", "ja"}, {"ja-JP", "ja"}, {"ko", "ko"}, {"ko-KR", "ko"},
                {"fr", "fr"}, {"fr-FR", "fr"}, {"de", "de"}, {"de-AT", "de"},
                {"es-ES", "es"}, {"es-419", "es-419"}, {"es-MX", "es-419"}, {"es-AR", "es-419"},
                {"ar", "ar"}, {"ar-SA", "ar"}, {"th", "th"}, {"th-TH", "th"},
                {"pt-BR", "pt-BR"}, {"pt-PT", "pt-PT"}, {"pt", "pt-PT"},
                {"bo", "bo"}, {"ug", "ug"},
                {"it", "en"}, {"ru", "en"},
        };
        for (int i = 0; i < tags.length; i++) {
            I18n.forceTag(tags[i][0]);
            eq("tag:" + tags[i][0], I18n.tag(), tags[i][1]);
        }

        // ---- 3. 输出正确 ----
        I18n.forceTag("zh");
        eq("zhGreet", I18n.t("拾光", "OneStar"), "拾光");
        eq("zhStub", I18n.t("不存在的键", "Stub"), "不存在的键");

        String[] latinOnly = {"en", "fr", "de", "es", "es-419", "pt-BR", "pt-PT", "th", "ar", "ug", "ko"};
        for (int i = 0; i < latinOnly.length; i++) {
            I18n.forceTag(latinOnly[i]);
            int leaked = 0;
            String sample = "";
            for (int r = 0; r < rows; r++) {
                String v = I18n.raw(I18n.lang(), r);
                if (hasHan(v)) {
                    leaked++;
                    if (sample.length() == 0) sample = I18n.zhKey(r);
                }
            }
            ok("noHan#" + latinOnly[i], leaked == 0, "leaked=" + leaked + " first=" + sample);
        }

        // 日语/韩语译文允许汉字，但不得整句等于中文原文
        for (int l = I18n.JA; l <= I18n.KO; l++) {
            int same = 0;
            for (int r = 0; r < rows; r++) {
                String zh = I18n.zhKey(r);
                String v = I18n.raw(l, r);
                // 152=星期表头「日」（日文本来就用「日」）；174=纯占位符模板，语序无关
                if (r == 152 || r == 174) continue;
                if (zh != null && zh.equals(v)) same++;
            }
            ok("notCopied#" + I18n.TAGS[l], same == 0, "same=" + same);
        }

        // t() 走表而不是走中文
        I18n.forceTag("ja");
        ok("jaNotZh", !"\u62fe\u5149".equals(I18n.t("拾光", "OneStar")), I18n.t("拾光", "OneStar"));
        I18n.forceTag("fr");
        ok("frNotZh", !hasHan(I18n.t("拾光", "OneStar")), I18n.t("拾光", "OneStar"));

        // 日期
        I18n.forceTag("zh");
        eq("zhDate", I18n.date(11, 4), "11 月 4 日");
        eq("zhMonthYear", I18n.monthYear(2026, 11), "2026 年 11 月");
        I18n.forceTag("ja");
        eq("jaDate", I18n.date(11, 4), "11月4日");
        eq("jaMonthYear", I18n.monthYear(2026, 11), "2026年11月");
        I18n.forceTag("fr");
        ok("frDate", !hasHan(I18n.date(11, 4)), I18n.date(11, 4));
        ok("frMonthYear", !hasHan(I18n.monthYear(2026, 11)), I18n.monthYear(2026, 11));
        I18n.forceTag("de");
        ok("deMonthYear", !hasHan(I18n.monthYear(2026, 11)), I18n.monthYear(2026, 11));

        // 星期 / 月名
        I18n.forceTag("zh");
        eq("zhWeekday0", I18n.weekday(0), "周日");
        I18n.forceTag("fr");
        ok("frWeekday0", !hasHan(I18n.weekday(0)), I18n.weekday(0));
        for (int m = 1; m <= 12; m++) {
            ok("frMonth" + m, !hasHan(I18n.month(m)), I18n.month(m));
            ok("frMonthShort" + m, !hasHan(I18n.monthShort(m)), I18n.monthShort(m));
        }

        // 数字与格式化
        I18n.forceTag("fr");
        eq("fDe", I18n.f("第 %d 颗", "#%d", Integer.valueOf(3)), "#3");
        ok("fDeNoHan", !hasHan(I18n.f("第 %d 颗", "#%d", Integer.valueOf(3))), I18n.f("第 %d 颗", "#%d", Integer.valueOf(3)));
        I18n.forceTag("zh");
        eq("fZh", I18n.f("第 %d 颗", "#%d", Integer.valueOf(3)), "第 3 颗");

        // 引号
        I18n.forceTag("zh");
        eq("quoteZh", I18n.quote("絮语"), "「絮语」");
        I18n.forceTag("en");
        eq("quoteEn", I18n.quote("Whisper"), "\u201cWhisper\u201d");

        // RTL
        I18n.forceTag("ar");
        ok("rtlAr", I18n.rtl(), "ar");
        I18n.forceTag("ug");
        ok("rtlUg", I18n.rtl(), "ug");
        I18n.forceTag("fr");
        ok("ltrFr", !I18n.rtl(), "fr");
        I18n.forceTag("ar");
        ok("visualAr", I18n.visual("مرحبا") != null, "null");
        ok("visualNum", I18n.num(7) != null, "null");

        // v6.7 字体缩放钳制：0.90 ~ 1.15，异常值按 1 处理
        eq("fsSmall", String.valueOf(I18n.fontScale(0.5f)), "0.9");
        eq("fsNormal", String.valueOf(I18n.fontScale(1f)), "1.0");
        eq("fsBig", String.valueOf(I18n.fontScale(2f)), "1.15");
        eq("fsMid", String.valueOf(I18n.fontScale(1.3f)), "1.15");
        eq("fsZero", String.valueOf(I18n.fontScale(0f)), "1.0");
        eq("fsNaN", String.valueOf(I18n.fontScale(Float.NaN)), "1.0");
        ok("fsMonotonic", I18n.fontScale(0.95f) < I18n.fontScale(1.05f), "order");

        // 恢复跟随系统
        I18n.forceTag(null);
        ok("restored", I18n.lang() >= 0 && I18n.lang() < I18n.COUNT, "lang=" + I18n.lang());

        System.out.println("PASS=" + pass + " FAIL=" + fail);
        if (fail > 0) System.exit(1);
    }
}
