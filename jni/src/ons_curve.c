/*
 * 拾光 v5.2 · libons_curve.so
 * 心情折线的原生平滑：[0.25, 0.5, 0.25] 三抽头，原地两遍，跳过 NaN 空缺段。
 * 不分配内存，只读写调用方给的数组，长度全部来自 Java 侧并做校验。
 */
#include <jni.h>

static int ons_isnan(float v) {
    return v != v;
}

static void ons_pass(float *a, int n) {
    if (n < 3) return;
    float prev = a[0];
    for (int i = 1; i < n - 1; i++) {
        float cur = a[i];
        float next = a[i + 1];
        if (!ons_isnan(prev) && !ons_isnan(cur) && !ons_isnan(next)) {
            a[i] = 0.25f * prev + 0.5f * cur + 0.25f * next;
        }
        prev = cur;
    }
}

/*
 * soften(float[] a, int n, int passes) —— 成功返回 0；非法参数返回 -1 / -2。
 */
JNIEXPORT jint JNICALL
Java_com_yangyang2857rgb_onestar_NativeCore_soften(JNIEnv *env, jclass cls,
                                          jfloatArray arr, jint n, jint passes) {
    (void) cls;
    if (arr == 0 || n <= 0) return -1;
    jsize len = (*env)->GetArrayLength(env, arr);
    if (n > len) return -2;
    if (passes <= 0) return 0;
    if (passes > 4) passes = 4;

    jfloat *a = (*env)->GetFloatArrayElements(env, arr, 0);
    if (a == 0) return -3;
    for (int p = 0; p < passes; p++) ons_pass(a, n);
    (*env)->ReleaseFloatArrayElements(env, arr, a, 0);
    return 0;
}
