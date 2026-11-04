package com.yangyang2857rgb.onestar;

/**
 * 拾光 v5.2 · 原生内核桥接。
 *
 * 三个库各自独立加载，任一失败只关闭对应快路径，功能回退到 Java 实现，不影响启动：
 * - libons_page.so  页大小 / 对齐探测（面板"内存页"一行的原生确认）
 * - libons_dsp.so   钟磬合成内核（ChimePlayer 的 PCM 生成）
 * - libons_curve.so 心情折线平滑（MoodCurve 的三抽头滤波）
 *
 * 三个库均以 -Wl,-z,max-page-size=16384 编译，ELF 段 p_align = 0x4000，
 * 并以不压缩方式存放在 lib/arm64-v8a 下、按 16 KB 对齐打包。
 */
final class NativeCore {

    static final boolean PAGE = load("ons_page");
    static final boolean DSP = load("ons_dsp");
    static final boolean CURVE = load("ons_curve");

    private NativeCore() {
    }

    private static boolean load(String name) {
        try {
            System.loadLibrary(name);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 已加载的原生库数量，用于面板展示。 */
    static int loadedCount() {
        int n = 0;
        if (PAGE) n++;
        if (DSP) n++;
        if (CURVE) n++;
        return n;
    }

    /** 设备真实页大小（字节），失败返回 0。 */
    static native int pageSize();

    /** 原生库编译期使用的最大页尺寸（常量 16384）。 */
    static native int pageAlign();

    /** 本机 ABI 位宽（64 / 32）。 */
    static native int elfBits();

    /**
     * 合成钟磬 PCM。
     *
     * @return 0 成功；-1 长度越界；-2 参数非法；-3 取数组失败
     */
    static native int chime(short[] pcm, int n, float f0, int sr);

    /** 折线平滑（原位，原地改写传入数组）。 */
    static native int soften(float[] arr, int n, int passes);
}
