/*
 * 拾光 v5.2 · libons_dsp.so
 * 钟磬合成内核：基频 + 三个泛音 + 单次延迟余响 + 起振/收尾包络，直接写入 16 bit PCM。
 * 与 ChimePlayer 的 Java 实现同构，作为原生快路径；不分配堆内存、不做越界访问。
 * 无 libm 依赖：正弦用自身多项式实现，指数衰减用逐样本乘子递推。
 */
#include <jni.h>

#define ONS_PI 3.14159265358979f
#define ONS_TWO_PI 6.28318530717959f

/* 项数足够的 sin 近似（Taylor 展开到 x^11），先把相位折算到 [-pi, pi] */
static float ons_sin(float x) {
    if (x != x) return 0.0f;                 /* NaN 保护 */
    if (x > 1.0e6f || x < -1.0e6f) return 0.0f;
    while (x > ONS_PI) x -= ONS_TWO_PI;
    while (x < -ONS_PI) x += ONS_TWO_PI;
    float x2 = x * x;
    float t = x * (1.0f
            - x2 / 6.0f
            + x2 * x2 / 120.0f
            - x2 * x2 * x2 / 5040.0f
            + x2 * x2 * x2 * x2 / 362880.0f
            - x2 * x2 * x2 * x2 * x2 / 39916800.0f);
    return t;
}

static float ons_clamp1(float v) {
    if (v > 1.0f) return 1.0f;
    if (v < -1.0f) return -1.0f;
    return v;
}

/*
 * chime(short[] pcm, int n, float f0, int sr)
 *   n 必须 <= pcm.length，否则返回 -1；参数非法返回 -2。成功返回 0。
 */
JNIEXPORT jint JNICALL
Java_com_yangyang2857rgb_onestar_NativeCore_chime(JNIEnv *env, jclass cls,
                                        jshortArray pcm, jint n, jfloat f0, jint sr) {
    (void) cls;
    if (pcm == 0 || n <= 0 || sr < 8000 || f0 < 20.0f || f0 > 12000.0f) return -2;
    jsize len = (*env)->GetArrayLength(env, pcm);
    if (n > len) return -1;

    jshort *buf = (*env)->GetShortArrayElements(env, pcm, 0);
    if (buf == 0) return -3;

    const float inv = 1.0f / (float) sr;
    /* 各泛音的指数衰减：逐样本乘子，等价于 exp(-t * k) */
    const float d1 = 3.1f * inv;
    const float d2 = 5.2f * inv;
    const float d3 = 7.8f * inv;
    const float d4 = 2.2f * inv;
    float e1 = 1.0f, e2 = 1.0f, e3 = 1.0f, e4 = 1.0f;
    const float m1 = 1.0f - d1, m2 = 1.0f - d2, m3 = 1.0f - d3, m4 = 1.0f - d4;

    const int delay = (int) (0.105f * (float) sr);
    const int atk = (int) (0.006f * (float) sr);
    const int fade = (int) (0.08f * (float) sr);

    for (int i = 0; i < n; i++) {
        float ph = ONS_TWO_PI * f0 * (float) i * inv;
        if (ph > 1.0e6f) {                   /* 长样本时避免相位过大丢精度 */
            float k = (float) (ph / ONS_TWO_PI);
            ph -= ONS_TWO_PI * (float) (long) k;
        }
        float v = 1.00f * ons_sin(ph) * e1
                + 0.40f * ons_sin(ph * 2.0f) * e2
                + 0.18f * ons_sin(ph * 3.01f) * e3
                + 0.08f * ons_sin(ph * 0.5f) * e4;
        /* 单次延迟余响（腔体感）：对已写出的样本做反馈 */
        if (i >= delay) {
            float prev = (float) buf[i - delay] / 32767.0f;
            v += prev * 0.28f;
        }
        if (i < atk) v *= (float) i / (float) atk;
        int back = n - 1 - i;
        if (back < fade) v *= (float) back / (float) fade;
        v = ons_clamp1(v * 0.32f);
        buf[i] = (jshort) (v * 32767.0f);

        e1 *= m1; e2 *= m2; e3 *= m3; e4 *= m4;
        if (e1 < 1e-7f) e1 = 0.0f;
        if (e2 < 1e-7f) e2 = 0.0f;
        if (e3 < 1e-7f) e3 = 0.0f;
        if (e4 < 1e-7f) e4 = 0.0f;
    }

    (*env)->ReleaseShortArrayElements(env, pcm, buf, 0);
    return 0;
}
