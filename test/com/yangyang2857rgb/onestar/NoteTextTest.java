package com.yangyang2857rgb.onestar;

/** 拾光 v2.1 备注文本单测：纯 JVM，不依赖 android */
public class NoteTextTest {

    static int pass = 0, fail = 0;

    static void eq(String name, Object got, Object want) {
        boolean ok = String.valueOf(got).equals(String.valueOf(want));
        if (ok) pass++;
        else {
            fail++;
            System.out.println("FAIL  " + name + "   got=" + got + "  want=" + want);
        }
    }

    public static void main(String[] a) {
        // 规整
        eq("trim", NoteText.clean("  风很轻  "), "风很轻");
        eq("collapse", NoteText.clean("风    很轻"), "风 很轻");
        eq("newline", NoteText.clean("第一行\n第二行"), "第一行 第二行");
        eq("tab", NoteText.clean("a\tb"), "a b");
        eq("null", NoteText.clean(null), "");
        eq("blank", NoteText.clean("   "), "");

        // 长度
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) sb.append('字');
        eq("len50->40", NoteText.clean(sb.toString()).length(), 40);
        eq("len40", NoteText.clean(sb.substring(0, 40)).length(), 40);
        StringBuilder emo = new StringBuilder();
        for (int i = 0; i < 45; i++) emo.append("\uD83C\uDF1F");
        eq("emoji40", NoteText.clean(emo.toString()).codePointCount(0, NoteText.clean(emo.toString()).length()), 40);

        // 转义
        eq("enc colon", NoteText.encode("今天:很好"), "今天%3A很好");
        eq("enc comma", NoteText.encode("a,b"), "a%2Cb");
        eq("enc pct", NoteText.encode("100%"), "100%25");
        eq("enc empty", NoteText.encode(""), "");
        eq("dec colon", NoteText.decode("今天%3A很好"), "今天:很好");
        eq("dec pct", NoteText.decode("100%25"), "100%");
        eq("dec bad", NoteText.decode("50%ZZ"), "50%ZZ");
        eq("dec bare", NoteText.decode("50%"), "50%");

        String raw = "风很轻，云:很白,100% 喜欢 🌟";
        eq("roundtrip", NoteText.decode(NoteText.encode(raw)), raw);

        // 存储行切分：date:mood:note 三段
        String line = "2026-11-04:2:" + NoteText.encode(raw);
        String[] f = line.split(":", -1);
        eq("seg count", f.length, 3);
        eq("seg date", f[0], "2026-11-04");
        eq("seg mood", f[1], "2");
        eq("seg note", NoteText.decode(f[2]), raw);

        // 旧格式兼容（两段，无备注）
        eq("legacy seg", "2026-11-03:1".split(":", -1).length, 2);
        eq("legacy note", decodeNull("2026-11-03:1"), "");

        System.out.println("PASS " + pass + "  FAIL " + fail);
        if (fail > 0) System.exit(1);
    }

    /** 模拟旧数据行没有第三段时取到空备注。 */
    static String decodeNull(String seg) {
        String[] f = seg.split(":", -1);
        return f.length > 2 ? NoteText.decode(f[2]) : "";
    }
}
