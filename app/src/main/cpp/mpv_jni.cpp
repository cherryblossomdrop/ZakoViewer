#include <jni.h>
#include <string>
#include <android/log.h>
#include <android/native_window_jni.h>

#define LOG_TAG "MPV_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// In full build with libmpv-dev, include <mpv/client.h> and <mpv/render_gl.h>
// Here we declare the JNI export signatures matching moe.zako.viewer.core.player.MPVLib

extern "C" {

JNIEXPORT void JNICALL
Java_moe_zako_viewer_core_player_MPVLib_create(JNIEnv *env, jobject thiz, jobject ctx) {
    LOGI("MPVLib native create invoked");
}

JNIEXPORT void JNICALL
Java_moe_zako_viewer_core_player_MPVLib_init(JNIEnv *env, jobject thiz) {
    LOGI("MPVLib native init invoked");
}

JNIEXPORT void JNICALL
Java_moe_zako_viewer_core_player_MPVLib_destroy(JNIEnv *env, jobject thiz) {
    LOGI("MPVLib native destroy invoked");
}

JNIEXPORT void JNICALL
Java_moe_zako_viewer_core_player_MPVLib_attachSurface(JNIEnv *env, jobject thiz, jobject surface) {
    LOGI("MPVLib native attachSurface invoked");
}

JNIEXPORT void JNICALL
Java_moe_zako_viewer_core_player_MPVLib_detachSurface(JNIEnv *env, jobject thiz) {
    LOGI("MPVLib native detachSurface invoked");
}

JNIEXPORT void JNICALL
Java_moe_zako_viewer_core_player_MPVLib_command(JNIEnv *env, jobject thiz, jobjectArray cmd) {
    LOGI("MPVLib native command invoked");
}

JNIEXPORT jint JNICALL
Java_moe_zako_viewer_core_player_MPVLib_setOptionString(JNIEnv *env, jobject thiz, jstring name, jstring value) {
    return 0;
}

JNIEXPORT jstring JNICALL
Java_moe_zako_viewer_core_player_MPVLib_getPropertyString(JNIEnv *env, jobject thiz, jstring property) {
    return nullptr;
}

JNIEXPORT jint JNICALL
Java_moe_zako_viewer_core_player_MPVLib_setPropertyString(JNIEnv *env, jobject thiz, jstring property, jstring value) {
    return 0;
}

JNIEXPORT jobject JNICALL
Java_moe_zako_viewer_core_player_MPVLib_getPropertyInt(JNIEnv *env, jobject thiz, jstring property) {
    return nullptr;
}

JNIEXPORT jint JNICALL
Java_moe_zako_viewer_core_player_MPVLib_setPropertyInt(JNIEnv *env, jobject thiz, jstring property, jint value) {
    return 0;
}

JNIEXPORT jobject JNICALL
Java_moe_zako_viewer_core_player_MPVLib_getPropertyDouble(JNIEnv *env, jobject thiz, jstring property) {
    return nullptr;
}

JNIEXPORT jint JNICALL
Java_moe_zako_viewer_core_player_MPVLib_setPropertyDouble(JNIEnv *env, jobject thiz, jstring property, jdouble value) {
    return 0;
}

JNIEXPORT jobject JNICALL
Java_moe_zako_viewer_core_player_MPVLib_getPropertyBoolean(JNIEnv *env, jobject thiz, jstring property) {
    return nullptr;
}

JNIEXPORT jint JNICALL
Java_moe_zako_viewer_core_player_MPVLib_setPropertyBoolean(JNIEnv *env, jobject thiz, jstring property, jboolean value) {
    return 0;
}

}
