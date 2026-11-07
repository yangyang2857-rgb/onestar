package com.yangyang2857rgb.onestar;

/**
 * v5.0 纯逻辑：SDK → 版本名 / 页大小文案 / 内存档文案。
 * 不 import 任何 android 类，可直接在 JVM 上跑单测。
 */
public final class CompatTable {

    private CompatTable() {
    }

    public static int major(int sdk) {
        if (sdk <= 21) return 5;
        if (sdk == 22) return 5;
        if (sdk == 23) return 6;
        if (sdk <= 25) return 7;
        if (sdk <= 27) return 8;
        if (sdk == 28) return 9;
        if (sdk == 29) return 10;
        if (sdk == 30) return 11;
        if (sdk <= 32) return 12;
        if (sdk == 33) return 13;
        if (sdk == 34) return 14;
        if (sdk == 35) return 15;
        if (sdk == 36) return 16;
        return 17;
    }

    public static String versionLabel(int sdk) {
        return "Android " + major(sdk) + " · API " + sdk;
    }

    /** 页大小字节数 → 文案，例如 16384 → "16 KB"。 */
    public static String pageLabel(int bytes) {
        if (bytes <= 0) return "? KB";
        return (bytes / 1024) + " KB";
    }

    public static boolean is16k(int bytes) {
        return bytes >= 16384;
    }

    /**
     * 内存页说明。
     *
     * v6.3 修正：旧文案在 16 KB 设备上直接返回「16 KB 页 · 页表更省」，
     * 把内核对大页的收益说成了 App 自己的能力，且不区分本包到底有没有原生库；
     * 在 4 KB 设备上又只谈库对齐、不提设备页。这里统一拆成
     * 「设备页大小（运行时实测）+ 原生库对齐情况（编译期事实）」两件事。
     */
    public static String pageNote(int bytes, boolean nativeAligned) {
        String device = is16k(bytes)
                ? I18n.t("16 KB 页", "16 KB page")
                : I18n.t("4 KB 页", "4 KB page");
        if (nativeAligned) {
            return device + I18n.t(" · 原生库已按 16 KB 对齐", " · native libs aligned");
        }
        return device + I18n.t(" · 无原生库 · 天然兼容", " · no native libs");
    }

    /** 只给兼容性结论，页大小由右侧数值列单独展示，避免同一行里说两遍。 */
    public static String pageCompat(boolean nativeLoaded) {
        if (nativeLoaded) return I18n.t("原生库已按 16 KB 对齐", "Native libs aligned to 16 KB");
        return I18n.t("无原生库 · 天然兼容", "No native libs, compatible by design");
    }

    /** 内存让位状态：0 标准 / 1 已让位 / 2 深度让位。 */
    public static String memBadge(int trimLevel) {
        if (trimLevel >= 2) return I18n.t("深度让位", "Deep trim");
        if (trimLevel == 1) return I18n.t("已让位", "Trimmed");
        return I18n.t("标准档", "Standard");
    }

    public static String memNote(boolean lowRam, int trimLevel) {
        if (lowRam) return I18n.t("低内存机 · 已自动降载", "Low-RAM device · auto reduced");
        if (trimLevel > 0) return I18n.t("系统回收 · 已让位", "Reclaimed by system · yielded");
        return I18n.t("标准档 · 可主动让位", "Standard · may yield on demand");
    }

    /** 历代刻度：SDK 与显示名成对返回，用于时间轴。 */
    public static int[] timelineSdk() {
        return new int[]{21, 23, 26, 29, 31, 33, 35, 36, 37};
    }

    public static String[] timelineName() {
        return new String[]{"5", "6", "8", "10", "12", "13", "15", "16", "17"};
    }

    /** 当前 SDK 在刻度轴上的下标；超出末位则取末位。 */
    public static int timelineIndex(int sdk) {
        int[] a = timelineSdk();
        int idx = 0;
        for (int i = 0; i < a.length; i++) {
            if (a[i] <= sdk) idx = i;
        }
        return idx;
    }
}
