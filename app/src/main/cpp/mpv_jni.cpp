#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/log.h>
#include <android/native_window_jni.h>

#define LOG_TAG "MPV_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// libmpv types & format enums
typedef struct mpv_handle mpv_handle;

typedef enum mpv_format {
    MPV_FORMAT_NONE             = 0,
    MPV_FORMAT_STRING           = 1,
    MPV_FORMAT_OSD_STRING       = 2,
    MPV_FORMAT_FLAG             = 3,
    MPV_FORMAT_INT64            = 4,
    MPV_FORMAT_DOUBLE           = 5,
    MPV_FORMAT_NODE             = 6,
    MPV_FORMAT_NODE_ARRAY       = 7,
    MPV_FORMAT_NODE_MAP         = 8,
    MPV_FORMAT_BYTE_ARRAY       = 9
} mpv_format;

// Directly linked from libmpv.so
extern "C" {
    mpv_handle* mpv_create(void);
    int mpv_initialize(mpv_handle *ctx);
    void mpv_terminate_destroy(mpv_handle *ctx);
    int mpv_command(mpv_handle *ctx, const char **args);
    int mpv_command_async(mpv_handle *ctx, uint64_t reply_userdata, const char **args);
    int mpv_set_option_string(mpv_handle *ctx, const char *name, const char *data);
    int mpv_set_property_string(mpv_handle *ctx, const char *name, const char *data);
    char* mpv_get_property_string(mpv_handle *ctx, const char *name);
    int mpv_set_property(mpv_handle *ctx, const char *name, mpv_format format, void *data);
    int mpv_get_property(mpv_handle *ctx, const char *name, mpv_format format, void *data);
    void mpv_free(void *data);
}

static mpv_handle* g_mpv = nullptr;
static std::mutex g_mpv_mutex;
static ANativeWindow* g_native_window = nullptr;

extern "C" {

JNIEXPORT void JNICALL
Java_moe_zako_viewer_core_player_MPVLib_create(JNIEnv *env, jobject thiz, jobject ctx) {
    std::lock_guard<std::mutex> lock(g_mpv_mutex);
    LOGI("MPVLib native create invoked");

    if (g_mpv) {
        mpv_terminate_destroy(g_mpv);
        g_mpv = nullptr;
    }

    g_mpv = mpv_create();
    LOGI("mpv_create instance: %p", g_mpv);
}

JNIEXPORT void JNICALL
Java_moe_zako_viewer_core_player_MPVLib_init(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_mpv_mutex);
    LOGI("MPVLib native init invoked");
    if (g_mpv) {
        int err = mpv_initialize(g_mpv);
        LOGI("mpv_initialize result: %d", err);
    }
}

JNIEXPORT void JNICALL
Java_moe_zako_viewer_core_player_MPVLib_destroy(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_mpv_mutex);
    LOGI("MPVLib native destroy invoked");
    if (g_mpv) {
        mpv_set_property_string(g_mpv, "vo", "null");
        int64_t wid = 0;
        mpv_set_property(g_mpv, "wid", MPV_FORMAT_INT64, &wid);
        mpv_terminate_destroy(g_mpv);
        g_mpv = nullptr;
    }
    if (g_native_window) {
        ANativeWindow_release(g_native_window);
        g_native_window = nullptr;
    }
}

JNIEXPORT void JNICALL
Java_moe_zako_viewer_core_player_MPVLib_attachSurface(JNIEnv *env, jobject thiz, jobject surface) {
    std::lock_guard<std::mutex> lock(g_mpv_mutex);
    LOGI("MPVLib native attachSurface invoked");
    if (!surface) return;
    if (g_native_window) {
        ANativeWindow_release(g_native_window);
        g_native_window = nullptr;
    }
    g_native_window = ANativeWindow_fromSurface(env, surface);
    if (g_mpv && g_native_window) {
        int64_t wid = reinterpret_cast<int64_t>(g_native_window);
        mpv_set_property(g_mpv, "wid", MPV_FORMAT_INT64, &wid);
        mpv_set_property_string(g_mpv, "vo", "gpu");
    }
}

JNIEXPORT void JNICALL
Java_moe_zako_viewer_core_player_MPVLib_detachSurface(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_mpv_mutex);
    LOGI("MPVLib native detachSurface invoked");
    if (g_mpv) {
        mpv_set_property_string(g_mpv, "vo", "null");
        int64_t wid = 0;
        mpv_set_property(g_mpv, "wid", MPV_FORMAT_INT64, &wid);
    }
    if (g_native_window) {
        ANativeWindow_release(g_native_window);
        g_native_window = nullptr;
    }
}

JNIEXPORT void JNICALL
Java_moe_zako_viewer_core_player_MPVLib_command(JNIEnv *env, jobject thiz, jobjectArray cmd) {
    std::lock_guard<std::mutex> lock(g_mpv_mutex);
    if (!g_mpv || !cmd) return;

    jsize size = env->GetArrayLength(cmd);
    std::vector<std::string> str_list;
    std::vector<const char*> c_args;
    str_list.reserve(size);
    c_args.reserve(size + 1);

    for (jsize i = 0; i < size; ++i) {
        auto jstr = (jstring)env->GetObjectArrayElement(cmd, i);
        if (jstr) {
            const char *chars = env->GetStringUTFChars(jstr, nullptr);
            if (chars) {
                str_list.emplace_back(chars);
                env->ReleaseStringUTFChars(jstr, chars);
            } else {
                str_list.emplace_back("");
            }
            env->DeleteLocalRef(jstr);
        } else {
            str_list.emplace_back("");
        }
    }
    for (const auto &s : str_list) {
        c_args.push_back(s.c_str());
    }
    c_args.push_back(nullptr);

    mpv_command(g_mpv, c_args.data());
}

JNIEXPORT void JNICALL
Java_moe_zako_viewer_core_player_MPVLib_commandAsync(JNIEnv *env, jobject thiz, jobjectArray cmd, jlong reply_userdata) {
    std::lock_guard<std::mutex> lock(g_mpv_mutex);
    if (!g_mpv || !cmd) return;

    jsize size = env->GetArrayLength(cmd);
    std::vector<std::string> str_list;
    std::vector<const char*> c_args;
    str_list.reserve(size);
    c_args.reserve(size + 1);

    for (jsize i = 0; i < size; ++i) {
        auto jstr = (jstring)env->GetObjectArrayElement(cmd, i);
        if (jstr) {
            const char *chars = env->GetStringUTFChars(jstr, nullptr);
            if (chars) {
                str_list.emplace_back(chars);
                env->ReleaseStringUTFChars(jstr, chars);
            } else {
                str_list.emplace_back("");
            }
            env->DeleteLocalRef(jstr);
        } else {
            str_list.emplace_back("");
        }
    }
    for (const auto &s : str_list) {
        c_args.push_back(s.c_str());
    }
    c_args.push_back(nullptr);

    mpv_command_async(g_mpv, static_cast<uint64_t>(reply_userdata), c_args.data());
}

JNIEXPORT jint JNICALL
Java_moe_zako_viewer_core_player_MPVLib_setOptionString(JNIEnv *env, jobject thiz, jstring name, jstring value) {
    std::lock_guard<std::mutex> lock(g_mpv_mutex);
    if (!g_mpv || !name || !value) return -1;

    const char *c_name = env->GetStringUTFChars(name, nullptr);
    const char *c_value = env->GetStringUTFChars(value, nullptr);
    int res = mpv_set_option_string(g_mpv, c_name, c_value);
    env->ReleaseStringUTFChars(name, c_name);
    env->ReleaseStringUTFChars(value, c_value);
    return res;
}

JNIEXPORT jstring JNICALL
Java_moe_zako_viewer_core_player_MPVLib_getPropertyString(JNIEnv *env, jobject thiz, jstring property) {
    std::lock_guard<std::mutex> lock(g_mpv_mutex);
    if (!g_mpv || !property) return nullptr;

    const char *c_prop = env->GetStringUTFChars(property, nullptr);
    char *res = mpv_get_property_string(g_mpv, c_prop);
    env->ReleaseStringUTFChars(property, c_prop);

    if (!res) return nullptr;
    jstring jres = env->NewStringUTF(res);
    mpv_free(res);
    return jres;
}

JNIEXPORT jint JNICALL
Java_moe_zako_viewer_core_player_MPVLib_setPropertyString(JNIEnv *env, jobject thiz, jstring property, jstring value) {
    std::lock_guard<std::mutex> lock(g_mpv_mutex);
    if (!g_mpv || !property || !value) return -1;

    const char *c_prop = env->GetStringUTFChars(property, nullptr);
    const char *c_val = env->GetStringUTFChars(value, nullptr);
    int res = mpv_set_property_string(g_mpv, c_prop, c_val);
    env->ReleaseStringUTFChars(property, c_prop);
    env->ReleaseStringUTFChars(value, c_val);
    return res;
}

JNIEXPORT jobject JNICALL
Java_moe_zako_viewer_core_player_MPVLib_getPropertyInt(JNIEnv *env, jobject thiz, jstring property) {
    std::lock_guard<std::mutex> lock(g_mpv_mutex);
    if (!g_mpv || !property) return nullptr;

    const char *c_prop = env->GetStringUTFChars(property, nullptr);
    int64_t val = 0;
    int err = mpv_get_property(g_mpv, c_prop, MPV_FORMAT_INT64, &val);
    env->ReleaseStringUTFChars(property, c_prop);
    if (err < 0) return nullptr;

    jclass integerClass = env->FindClass("java/lang/Integer");
    jmethodID mid = env->GetMethodID(integerClass, "<init>", "(I)V");
    return env->NewObject(integerClass, mid, static_cast<jint>(val));
}

JNIEXPORT jint JNICALL
Java_moe_zako_viewer_core_player_MPVLib_setPropertyInt(JNIEnv *env, jobject thiz, jstring property, jint value) {
    std::lock_guard<std::mutex> lock(g_mpv_mutex);
    if (!g_mpv || !property) return -1;

    const char *c_prop = env->GetStringUTFChars(property, nullptr);
    int64_t val = value;
    int res = mpv_set_property(g_mpv, c_prop, MPV_FORMAT_INT64, &val);
    env->ReleaseStringUTFChars(property, c_prop);
    return res;
}

JNIEXPORT jobject JNICALL
Java_moe_zako_viewer_core_player_MPVLib_getPropertyDouble(JNIEnv *env, jobject thiz, jstring property) {
    std::lock_guard<std::mutex> lock(g_mpv_mutex);
    if (!g_mpv || !property) return nullptr;

    const char *c_prop = env->GetStringUTFChars(property, nullptr);
    double val = 0.0;
    int err = mpv_get_property(g_mpv, c_prop, MPV_FORMAT_DOUBLE, &val);
    env->ReleaseStringUTFChars(property, c_prop);
    if (err < 0) return nullptr;

    jclass doubleClass = env->FindClass("java/lang/Double");
    jmethodID mid = env->GetMethodID(doubleClass, "<init>", "(D)V");
    return env->NewObject(doubleClass, mid, val);
}

JNIEXPORT jint JNICALL
Java_moe_zako_viewer_core_player_MPVLib_setPropertyDouble(JNIEnv *env, jobject thiz, jstring property, jdouble value) {
    std::lock_guard<std::mutex> lock(g_mpv_mutex);
    if (!g_mpv || !property) return -1;

    const char *c_prop = env->GetStringUTFChars(property, nullptr);
    double val = value;
    int res = mpv_set_property(g_mpv, c_prop, MPV_FORMAT_DOUBLE, &val);
    env->ReleaseStringUTFChars(property, c_prop);
    return res;
}

JNIEXPORT jobject JNICALL
Java_moe_zako_viewer_core_player_MPVLib_getPropertyBoolean(JNIEnv *env, jobject thiz, jstring property) {
    std::lock_guard<std::mutex> lock(g_mpv_mutex);
    if (!g_mpv || !property) return nullptr;

    const char *c_prop = env->GetStringUTFChars(property, nullptr);
    int val = 0;
    int err = mpv_get_property(g_mpv, c_prop, MPV_FORMAT_FLAG, &val);
    env->ReleaseStringUTFChars(property, c_prop);
    if (err < 0) return nullptr;

    jclass booleanClass = env->FindClass("java/lang/Boolean");
    jmethodID mid = env->GetMethodID(booleanClass, "<init>", "(Z)V");
    return env->NewObject(booleanClass, mid, val ? JNI_TRUE : JNI_FALSE);
}

JNIEXPORT jint JNICALL
Java_moe_zako_viewer_core_player_MPVLib_setPropertyBoolean(JNIEnv *env, jobject thiz, jstring property, jboolean value) {
    std::lock_guard<std::mutex> lock(g_mpv_mutex);
    if (!g_mpv || !property) return -1;

    const char *c_prop = env->GetStringUTFChars(property, nullptr);
    int val = (value == JNI_TRUE) ? 1 : 0;
    int res = mpv_set_property(g_mpv, c_prop, MPV_FORMAT_FLAG, &val);
    env->ReleaseStringUTFChars(property, c_prop);
    return res;
}

}