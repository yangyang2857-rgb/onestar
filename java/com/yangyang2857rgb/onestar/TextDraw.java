package com.yangyang2857rgb.onestar;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

/**
 * Canvas 文字绘制的统一入口（v6.6 多语言改造）。
 *
 * 背景：Android 的 {@link Canvas#drawText(CharSequence, float, float, Paint)} 不做 bidi 重排，
 * 阿拉伯语 / 维吾尔语这类从右往左的语言直接画会得到反序甚至断行的结果。
 * 英文、中文等 LTR 语言仍走原来的 drawText（行为与 v6.5 完全一致，零回归成本），
 * 只有当前语言是 RTL 时才用 {@link StaticLayout}（Minikin 内部做 bidi + shaping）绘制。
 *
 * 该类的存在也让 I18n.java 继续保持「不 import android.*」，
 * 从而能被 test.sh 的纯 JVM 单测直接编译。
 */
final class TextDraw {

    private TextDraw() {
    }

    static void draw(Canvas c, String text, float x, float y, Paint p) {
        if (c == null || p == null || text == null || text.length() == 0) return;
        if (!I18n.rtl()) {
            c.drawText(text, x, y, p);
            return;
        }
        float w = p.measureText(text);
        int width = Math.max(1, (int) Math.ceil(w) + 4);
        Paint.Align align = p.getTextAlign();
        Layout.Alignment la = Layout.Alignment.ALIGN_NORMAL;
        float left = x;
        if (align == Paint.Align.CENTER) {
            la = Layout.Alignment.ALIGN_CENTER;
            left = x - width / 2f;
        } else if (align == Paint.Align.RIGHT) {
            la = Layout.Alignment.ALIGN_OPPOSITE;
            left = x - width;
        }
        StaticLayout layout = new StaticLayout(text, new TextPaint(p), width, la, 1f, 0f, false);
        int save = c.save();
        try {
            c.translate(left, y - layout.getLineBaseline(0));
            layout.draw(c);
        } finally {
            c.restoreToCount(save);
        }
    }
}
