/*
 * 拾光 v5.2 · libons_page.so
 * 原生侧的页大小 / 对齐探测。仅做只读查询，无权限、无外部状态。
 * 编译：-Wl,-z,max-page-size=16384，确保 ELF 段按 16 KB 对齐。
 */
#include <jni.h>

/* bionic: _SC_PAGESIZE == 39（aarch64 / armv7 一致） */
extern long sysconf(int);

#define ONS_SC_PAGESIZE 39

JNIEXPORT jint JNICALL
Java_com_yangyang2857rgb_onestar_NativeCore_pageSize(JNIEnv *env, jclass cls) {
    (void) env;
    (void) cls;
    long v = sysconf(ONS_SC_PAGESIZE);
    return (v > 0) ? (jint) v : 0;
}

JNIEXPORT jint JNICALL
Java_com_yangyang2857rgb_onestar_NativeCore_pageAlign(JNIEnv *env, jclass cls) {
    (void) env;
    (void) cls;
    /* 本库编译期使用的最大页尺寸，供 Java 侧核对打包是否按 16 KB 对齐 */
    return 16384;
}

JNIEXPORT jint JNICALL
Java_com_yangyang2857rgb_onestar_NativeCore_elfBits(JNIEnv *env, jclass cls) {
    (void) env;
    (void) cls;
    return (jint) (sizeof(void *) * 8);
}
