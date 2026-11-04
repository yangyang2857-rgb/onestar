package com.yangyang2857rgb.onestar;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 拾光 v4.0「年度星图长图」：把一年的夜空排成一张可以存进相册的长图。
 * 全部用 Canvas 绘制，不依赖第三方库，也不申请任何权限：
 * Android 10+ 走 MediaStore 存进相册；更早的版本退化为应用自己的图片目录。
 */
final class PosterRenderer {

    private static final int W = 1080;
    private static final int H = 2160;

    private static final int C_TEXT = 0xFFEEF3FF;
    private static final int C_DIM = 0xFF8A97BD;
    private static final int C_WARM = 0xFFFFF0D0;
    private static final int C_BG = 0xFF04050D;

    private PosterRenderer() {
    }

    /** 导出长图，返回落地路径（失败返回 null）。 */
    static String export(Context ctx) {
        List<String> days = new ArrayList<String>();
        Map<String, Integer> mood = new HashMap<String, Integer>();
        Map<String, String> notes = new HashMap<String, String>();
        Map<String, String> names = new HashMap<String, String>();
        java.util.Set<String> back = new java.util.HashSet<String>();
        DaysStore.load(ctx, days, mood, notes, names, back);
        java.util.Collections.sort(days);
        if (days.isEmpty()) return null;

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Bitmap out;
        try {
            draw(bmp, days, mood, names, back, today);
            out = bmp;
        } catch (Throwable t) {
            bmp.recycle();
            return null;
        }

        String fileName = "拾光_" + today + ".jpg";
        try {
            String path = save(ctx, out, fileName);
            out.recycle();
            return path;
        } catch (Throwable t) {
            out.recycle();
            return null;
        }
    }

    private static String save(Context ctx, Bitmap bmp, String fileName) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver cr = ctx.getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/拾光");
            Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri != null) {
                OutputStream os = cr.openOutputStream(uri);
                if (os != null) {
                    bmp.compress(Bitmap.CompressFormat.JPEG, 94, os);
                    os.close();
                    return Environment.DIRECTORY_PICTURES + "/拾光/" + fileName;
                }
            }
        }
        File dir = ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (dir == null) dir = ctx.getFilesDir();
        File f = new File(dir, fileName);
        FileOutputStream fos = new FileOutputStream(f);
        bmp.compress(Bitmap.CompressFormat.JPEG, 94, fos);
        fos.close();
        return f.getAbsolutePath();
    }

    private static void draw(Bitmap bmp, List<String> days, Map<String, Integer> mood,
                             Map<String, String> names, java.util.Set<String> back, String today) {
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // 夜空底
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, 0, W * 0.4f, H,
                new int[]{0xFF070B1A, 0xFF04050D, 0xFF02030A}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(new RadialGradient(W * 0.72f, H * 0.16f, W * 0.78f,
                new int[]{0x22526AA8, 0x0A0A1630, 0x0004050D},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(null);

        // 底噪星星
        for (int i = 0; i < 260; i++) {
            float x = StarFieldView.hash01("px" + i) * W;
            float y = StarFieldView.hash01("py" + i) * H;
            float r = 0.7f + StarFieldView.hash01("pr" + i) * 1.5f;
            p.setColor(0xFFFFFFFF);
            p.setAlpha((int) (30 + StarFieldView.hash01("pa" + i) * 90));
            c.drawCircle(x, y, r, p);
        }
        p.setAlpha(255);

        int total = days.size();
        int streak = Constellation.streakOf(days, today);
        int span = Math.max(1, (int) ((Constellation.parseDate(days.get(days.size() - 1))
                - Constellation.parseDate(days.get(0))) / 86400000L) + 1);
        int year = Constellation.busiestYear(days, Integer.parseInt(today.substring(0, 4)));

        // 页头
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(74);
        p.setColor(C_TEXT);
        c.drawText("拾光", 86, 178, p);
        p.setTypeface(Typeface.DEFAULT);
        p.setTextSize(24);
        p.setColor(C_DIM);
        p.setLetterSpacing(0.34f);
        c.drawText("ONE STAR A DAY", 92, 226, p);
        p.setLetterSpacing(0f);

        p.setTextAlign(Paint.Align.RIGHT);
        p.setTextSize(30);
        p.setColor(C_WARM);
        c.drawText(year + " 年度星图", W - 86, 168, p);
        p.setTextSize(22);
        p.setColor(C_DIM);
        c.drawText(today.replace("-", " · "), W - 86, 214, p);
        p.setTextAlign(Paint.Align.LEFT);

        // 数值行
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(96);
        p.setColor(C_TEXT);
        c.drawText(String.valueOf(total), 86, 372, p);
        p.setTypeface(Typeface.DEFAULT);
        p.setTextSize(26);
        p.setColor(C_DIM);
        c.drawText("颗星", 86 + 232, 360, p);
        p.setTextSize(24);
        c.drawText("最长连续 " + streak + " 天    ·    陪伴 " + span + " 天    ·    最常 "
                + Constellation.MOOD_NAME[Constellation.dominantMood(days, mood)], 88, 424, p);

        // 星图穹顶
        RectF sky = new RectF(86, 476, W - 86, 1240);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2);
        p.setColor(0x1AFFFFFF);
        c.drawRoundRect(sky, 32, 32, p);
        p.setStyle(Paint.Style.FILL);

        List<float[]> pts = new ArrayList<float[]>();
        for (String d : days) {
            float x = sky.left + 40 + (0.06f + 0.88f * StarFieldView.hash01("u" + d)) * (sky.width() - 80);
            float y = sky.top + 40 + (0.10f + 0.80f * StarFieldView.hash01("v" + d)) * (sky.height() - 80);
            Integer m = mood.get(d);
            pts.add(new float[]{x, y, m == null ? 0 : m});
        }

        // 按月连线
        Path pa = new Path();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.6f);
        for (int mi = 0; mi < 12; mi++) {
            String mk = year + "-" + (mi < 9 ? "0" : "") + (mi + 1);
            boolean started = false;
            pa.reset();
            for (int i = 0; i < days.size(); i++) {
                if (!days.get(i).startsWith(mk)) continue;
                float[] q = pts.get(i);
                if (!started) {
                    pa.moveTo(q[0], q[1]);
                    started = true;
                } else {
                    pa.lineTo(q[0], q[1]);
                }
            }
            if (!started) continue;
            p.setColor(0xFFBFD2FF);
            p.setAlpha(46);
            c.drawPath(pa, p);
        }
        p.setStyle(Paint.Style.FILL);

        // 星点
        for (int i = 0; i < pts.size(); i++) {
            float[] q = pts.get(i);
            int m = (int) q[2];
            if (m < 0 || m >= Constellation.MOOD_N) m = 0;
            p.setColor(Constellation.MOOD_GLOW[m]);
            p.setAlpha(56);
            c.drawCircle(q[0], q[1], 9f, p);
            p.setColor(Constellation.MOOD_COLOR[m]);
            p.setAlpha(238);
            c.drawCircle(q[0], q[1], 4.2f, p);
            p.setColor(0xFFFFFFFF);
            p.setAlpha(170);
            c.drawCircle(q[0], q[1], 1.5f, p);

            // v6.2：事后补记的那一天，外面套一圈虚线
            if (back != null && back.contains(days.get(i))) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.6f);
                p.setPathEffect(new DashPathEffect(new float[]{4f, 4f}, 0));
                p.setColor(0xFFFFD9A0);
                p.setAlpha(215);
                c.drawCircle(q[0], q[1], 12f, p);
                p.setPathEffect(null);
                p.setStyle(Paint.Style.FILL);
            }
        }
        p.setAlpha(255);

        // 补记图例（只有真的补过才出现）
        if (back != null && !back.isEmpty()) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.6f);
            p.setPathEffect(new DashPathEffect(new float[]{4f, 4f}, 0));
            p.setColor(0xFFFFD9A0);
            p.setAlpha(215);
            float lx = sky.right - 148;
            float ly = sky.bottom - 26;
            c.drawCircle(lx, ly, 8f, p);
            p.setPathEffect(null);
            p.setStyle(Paint.Style.FILL);
            p.setAlpha(190);
            p.setTextSize(20);
            p.setColor(C_WARM);
            c.drawText("虚线圈＝补记 " + back.size() + " 天", lx + 16, ly + 7, p);
            p.setAlpha(255);
        }

        // 穹顶内标题
        p.setTextSize(22);
        p.setColor(C_DIM);
        p.setLetterSpacing(0.2f);
        c.drawText("这 " + span + " 天里，你一共亮起 " + total + " 次", sky.left + 40, sky.top + 52, p);
        p.setLetterSpacing(0f);

        // 12 个月柱
        float chartTop = 1318, chartH = 210;
        int[] counts = Constellation.monthlyCounts(days, year);
        int[] colors = Constellation.monthlyColors(days, mood, year);
        int maxC = 1;
        for (int v : counts) if (v > maxC) maxC = v;
        float gap = 14f;
        float bw = (W - 172 - gap * 11) / 12f;
        float baseY = chartTop + chartH;
        p.setTextSize(22);
        p.setColor(C_DIM);
        c.drawText(year + " 年星盘", 86, chartTop - 24, p);
        for (int i = 0; i < 12; i++) {
            float bx = 86 + i * (bw + gap);
            p.setColor(0x14FFFFFF);
            c.drawRoundRect(new RectF(bx, chartTop, bx + bw, baseY), 6, 6, p);
            if (counts[i] > 0) {
                float h = Math.max(14f, chartH * counts[i] / (float) maxC);
                p.setColor(colors[i]);
                p.setAlpha(220);
                c.drawRoundRect(new RectF(bx, baseY - h, bx + bw, baseY), 6, 6, p);
                p.setAlpha(255);
                p.setColor(C_DIM);
                p.setTextSize(18);
                p.setTextAlign(Paint.Align.CENTER);
                c.drawText(String.valueOf(counts[i]), bx + bw / 2, baseY - h - 10, p);
                p.setTextAlign(Paint.Align.LEFT);
            }
            p.setColor(C_DIM);
            p.setTextSize(18);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText(String.valueOf(i + 1), bx + bw / 2, baseY + 30, p);
            p.setTextAlign(Paint.Align.LEFT);
        }

        // 心情曲线
        float curveTop = 1636, curveH = 190;
        float[] series = MoodCurve.weekly(days, mood, today, 6);
        p.setTextSize(22);
        p.setColor(C_DIM);
        c.drawText("近六周心情曲线", 86, curveTop - 22, p);
        p.setTextAlign(Paint.Align.RIGHT);
        p.setColor(C_WARM);
        c.drawText(MoodCurve.caption(series), W - 86, curveTop - 22, p);
        p.setTextAlign(Paint.Align.LEFT);

        Path cp = new Path();
        boolean st = false;
        int n = series.length;
        float stepX = n > 1 ? (W - 172) / (float) (n - 1) : 0;
        for (int i = 0; i < n; i++) {
            if (Float.isNaN(series[i])) {
                st = false;
                continue;
            }
            float x = 86 + i * stepX;
            float y = curveTop + curveH - curveH * Math.max(0f, Math.min(1f, series[i]));
            if (!st) {
                cp.moveTo(x, y);
                st = true;
            } else {
                cp.lineTo(x, y);
            }
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(C_WARM);
        p.setAlpha(210);
        c.drawPath(cp, p);
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < n; i++) {
            if (Float.isNaN(series[i])) continue;
            float x = 86 + i * stepX;
            float y = curveTop + curveH - curveH * Math.max(0f, Math.min(1f, series[i]));
            p.setColor(C_WARM);
            c.drawCircle(x, y, 6f, p);
        }
        p.setColor(0x14FFFFFF);
        c.drawRect(86, curveTop + curveH, W - 86, curveTop + curveH + 2, p);

        // 星名清单（最多 4 条）
        float ny = 1990;
        p.setTextSize(22);
        p.setColor(C_DIM);
        c.drawText("有名字的星星", 86, ny - 30, p);
        int shown = 0;
        for (int i = 0; i < days.size() && shown < 4; i++) {
            String d = days.get(i);
            String nm = names.get(d);
            if (nm == null || nm.length() == 0) continue;
            Integer m = mood.get(d);
            int mm = m == null ? 0 : m;
            p.setColor(Constellation.MOOD_COLOR[mm]);
            c.drawCircle(92, ny - 8 - shown * 40, 5f, p);
            p.setColor(C_TEXT);
            p.setTextSize(24);
            c.drawText("「" + nm + "」", 112, ny - shown * 40, p);
            p.setColor(C_DIM);
            p.setTextSize(20);
            p.setTextAlign(Paint.Align.RIGHT);
            c.drawText(d.substring(5).replace("-", "/") + "  " + Constellation.MOOD_NAME[mm],
                    W - 86, ny - shown * 40, p);
            p.setTextAlign(Paint.Align.LEFT);
            shown++;
        }
        if (shown == 0) {
            p.setColor(C_DIM);
            p.setTextSize(22);
            c.drawText("还没有给任何一颗星起名 · 长按星星可以起名", 86, ny, p);
        }

        // 页脚
        p.setTextSize(24);
        p.setColor(C_DIM);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText("每一颗，都是你认真过的一天", W / 2f, H - 96, p);
        p.setTextSize(18);
        p.setColor(0x668A97BD);
        c.drawText("拾光 v4.0 · 星轨回放 · 生成于 " + today, W / 2f, H - 56, p);
        p.setTextAlign(Paint.Align.LEFT);
        p.setColor(Color.WHITE);
    }
}
