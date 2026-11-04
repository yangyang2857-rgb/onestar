package com.yangyang2857rgb.onestar;

import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.system.Os;
import android.system.OsConstants;
import android.util.DisplayMetrics;
import android.view.WindowManager;

/**
 * v5.0 系统适配层：把「历代 Android 特性」落成可观测的事实，而不是装饰文案。
 *
 * 覆盖：
 * - Android 5.0(21) 起：沉浸全屏、纯 Java 无原生库
 * - Android 10(29)：手势导航、系统暗色
 * - Android 12(31)：RenderEffect 柔光阴影（有硬件层时）
 * - Android 13(33)：预测式返回（系统回调，见 MainActivity）
 * - Android 15(35)：强制边到边、请求式帧率
 * - Android 16/17(36/37)：16 KB 内存页、更严格的内存公平（MemoryLimiter）
 */
public final class Compat {

    public static final int SDK = Build.VERSION.SDK_INT;

    private Compat() {
    }

    /** 系统页大小（字节）。16 KB 页设备会返回 16384。API 21+ 可用，无需权限。 */
    public static int pageSize() {
        if (NativeCore.PAGE) {
            try {
                int v = NativeCore.pageSize();
                if (v > 0) return v;
            } catch (Throwable ignored) {
            }
        }
        try {
            long v = Os.sysconf(OsConstants._SC_PAGESIZE);
            if (v > 0) return (int) v;
        } catch (Throwable ignored) {
        }
        return 4096;
    }

    public static boolean is16k() {
        return CompatTable.is16k(pageSize());
    }

    public static String pageLabel() {
        return CompatTable.pageLabel(pageSize());
    }

    public static String pageNote() {
        return CompatTable.pageNote(pageSize(), NativeCore.PAGE);
    }

    public static int memoryClass(Context c) {
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) return am.getMemoryClass();
        } catch (Throwable ignored) {
        }
        return 128;
    }

    public static boolean lowRam(Context c) {
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            return am != null && am.isLowRamDevice();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 屏幕刷新率（Hz），自适应刷新率设备会随内容变化。 */
    public static float refreshRate(Context c) {
        try {
            WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null && wm.getDefaultDisplay() != null) {
                return wm.getDefaultDisplay().getRefreshRate();
            }
        } catch (Throwable ignored) {
        }
        return 60f;
    }

    public static String refreshLabel(Context c) {
        float hz = refreshRate(c);
        int r = Math.round(hz);
        return r + " Hz";
    }

    public static boolean nightMode(Context c) {
        try {
            int m = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return m == Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** 是否需要按低内存策略降载（低内存机 / 小堆）。 */
    public static boolean shouldTrim(Context c) {
        return lowRam(c) || memoryClass(c) <= 192;
    }

    public static boolean canBlur() {
        return SDK >= 31;
    }

    public static boolean hasPredictiveBack() {
        return SDK >= 33;
    }

    public static boolean hasEdgeToEdge() {
        return SDK >= 30;
    }

    public static boolean hasRequestedFrameRate() {
        return SDK >= 35;
    }

    /** 主版本号，例如 17。 */
    public static int major() {
        return CompatTable.major(SDK);
    }

    /** 形如 "Android 17 · API 37"。 */
    public static String versionLabel() {
        return CompatTable.versionLabel(SDK);
    }
}
