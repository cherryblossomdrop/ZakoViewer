#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <thread>
#include <atomic>
#include <dlfcn.h>
#include <android/log.h>

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

typedef enum mpv_event_id {
    MPV_EVENT_NONE              = 0,
    MPV_EVENT_SHUTDOWN          = 1,
    MPV_EVENT_LOG_MESSAGE       = 2,
    MPV_EVENT_GET_PROPERTY_REPLY= 3,
    MPV_EVENT_SET_PROPERTY_REPLY= 4,
    MPV_EVENT_COMMAND_REPLY     = 5,
    MPV_EVENT_START_FILE        = 6,
    MPV_EVENT_END_FILE          = 7,
    MPV_EVENT_FILE_LOADED       = 8,
    MPV_EVENT_TRACKS_CHANGED    = 9,
    MPV_EVENT_TRACK_SWITCHED    = 10,
    MPV_EVENT_IDLE              = 11,
    MPV_EVENT_PAUSE             = 12,
    MPV_EVENT_UNPAUSE           = 13,
    MPV_EVENT_TICK              = 14,
    MPV_EVENT_SCRIPT_INPUT_DISPATCH = 15,
    MPV_EVENT_CLIENT_MESSAGE    = 16,
    MPV_EVENT_VIDEO_RECONFIG    = 17,
    MPV_EVENT_AUDIO_RECONFIG    = 18,
    MPV_EVENT_METADATA_UPDATE   = 19,
    MPV_EVENT_SEEK              = 20,
    MPV_EVENT_PLAYBACK_RESTART  = 21,
    MPV_EVENT_PROPERTY_CHANGE   = 22,
    MPV_EVENT_CHAPTER_CHANGE    = 23,
    MPV_EVENT_QUEUE_OVERFLOW    = 24,
    MPV_EVENT_HOOK              = 25
} mpv_event_id;

typedef enum mpv_log_level {
    MPV_LOG_LEVEL_NONE          = 0,
    MPV_LOG_LEVEL_FATAL         = 10,
    MPV_LOG_LEVEL_ERROR         = 20,
    MPV_LOG_LEVEL_WARN          = 30,
    MPV_LOG_LEVEL_INFO          = 40,
    MPV_LOG_LEVEL_V             = 50,
    MPV_LOG_LEVEL_DEBUG         = 60,
    MPV_LOG_LEVEL_TRACE         = 70
} mpv_log_level;

typedef struct mpv_event_log_message {
    const char *prefix;
    const char *level;
    const char *text;
    mpv_log_level log_level;
} mpv_event_log_message;

typedef struct mpv_event_property {
    const char *name;
    mpv_format format;
    void *data;
} mpv_event_property;

typedef struct mpv_event {
    mpv_event_id event_id;
    int error;
    uint64_t reply_userdata;
    void *data;
} mpv_event;

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
    int mpv_request_log_messages(mpv_handle *ctx, const char *min_level);
    mpv_event* mpv_wait_event(mpv_handle *ctx, double timeout);
    void mpv_wakeup(mpv_handle *ctx);
}

static JavaVM* g_vm = nullptr;
static jclass g_mpvlib_class = nullptr;
static jmethodID g_mid_log = nullptr;
static jmethodID g_mid_prop = nullptr;
static jmethodID g_mid_event = nullptr;

static mpv_handle* g_mpv = nullptr;
static std::mutex g_mpv_mutex;
static jobject g_surface = nullptr;
static jobject g_app_ctx = nullptr;

static std::thread g_event_thread;
static std::atomic<bool> g_event_running{false};

typedef int (*av_jni_set_java_vm_fn)(void *vm, void *log_ctx);
typedef int (*av_jni_set_android_app_ctx_fn)(void *app_ctx, void *log_ctx);

static void set_ffmpeg_java_vm(JavaVM *vm) {
    if (!vm) return;
    auto fn = reinterpret_cast<av_jni_set_java_vm_fn>(dlsym(RTLD_DEFAULT, "av_jni_set_java_vm"));
    if (!fn) {
        void *lib = dlopen("libavcodec.so", RTLD_NOW | RTLD_GLOBAL);
        if (lib) {
            fn = reinterpret_cast<av_jni_set_java_vm_fn>(dlsym(lib, "av_jni_set_java_vm"));
        }
    }
    if (fn) {
        int res = fn(vm, nullptr);
        LOGI("av_jni_set_java_vm called successfully, result: %d", res);
    } else {
        LOGE("Failed to find av_jni_set_java_vm symbol: %s", dlerror());
    }
}

static void set_ffmpeg_android_app_ctx(JNIEnv *env, jobject app_ctx) {
    if (!app_ctx || !env) return;
    if (g_app_ctx) {
        env->DeleteGlobalRef(g_app_ctx);
        g_app_ctx = nullptr;
    }
    g_app_ctx = env->NewGlobalRef(app_ctx);
    auto fn = reinterpret_cast<av_jni_set_android_app_ctx_fn>(dlsym(RTLD_DEFAULT, "av_jni_set_android_app_ctx"));
    if (!fn) {
        void *lib = dlopen("libavcodec.so", RTLD_NOW | RTLD_GLOBAL);
        if (lib) {
            fn = reinterpret_cast<av_jni_set_android_app_ctx_fn>(dlsym(lib, "av_jni_set_android_app_ctx"));
        }
    }
    if (fn) {
        int res = fn(g_app_ctx, nullptr);
        LOGI("av_jni_set_android_app_ctx called successfully, result: %d", res);
    } else {
        LOGW("av_jni_set_android_app_ctx symbol not found (not critical)");
    }
}

static void event_loop() {
    if (!g_vm) {
        LOGE("event_loop: g_vm is null");
        return;
    }
    JNIEnv *env = nullptr;
    jint res = g_vm->AttachCurrentThread(&env, nullptr);
    if (res != JNI_OK || !env) {
        LOGE("Failed to attach event thread to JVM: %d", res);
        return;
    }

    LOGI("MPV event loop thread started");

    while (g_event_running.load()) {
        mpv_handle *ctx = nullptr;
        {
            std::lock_guard<std::mutex> lock(g_mpv_mutex);
            ctx = g_mpv;
        }
        if (!ctx) break;

        mpv_event *event = mpv_wait_event(ctx, 0.2);
        if (!event || event->event_id == MPV_EVENT_NONE) continue;
        if (event->event_id == MPV_EVENT_SHUTDOWN) {
            LOGI("Received MPV_EVENT_SHUTDOWN");
            break;
        }

        if (event->event_id == MPV_EVENT_LOG_MESSAGE) {
            auto *msg = static_cast<mpv_event_log_message*>(event->data);
            if (msg) {
                int android_level = ANDROID_LOG_INFO;
                if (msg->log_level <= MPV_LOG_LEVEL_FATAL) android_level = ANDROID_LOG_FATAL;
                else if (msg->log_level <= MPV_LOG_LEVEL_ERROR) android_level = ANDROID_LOG_ERROR;
                else if (msg->log_level <= MPV_LOG_LEVEL_WARN) android_level = ANDROID_LOG_WARN;
                else if (msg->log_level <= MPV_LOG_LEVEL_INFO) android_level = ANDROID_LOG_INFO;
                else android_level = ANDROID_LOG_DEBUG;

                __android_log_print(android_level, LOG_TAG, "[%s] %s", msg->prefix ? msg->prefix : "mpv", msg->text ? msg->text : "");

                if (g_mpvlib_class && g_mid_log) {
                    jstring jprefix = env->NewStringUTF(msg->prefix ? msg->prefix : "");
                    jstring jtext = env->NewStringUTF(msg->text ? msg->text : "");
                    env->CallStaticVoidMethod(g_mpvlib_class, g_mid_log, jprefix, static_cast<jint>(msg->log_level), jtext);
                    env->DeleteLocalRef(jprefix);
                    env->DeleteLocalRef(jtext);
                    if (env->ExceptionCheck()) {
                        env->ExceptionClear();
                    }
                }
            }
        } else if (event->event_id == MPV_EVENT_PROPERTY_CHANGE) {
            auto *prop = static_cast<mpv_event_property*>(event->data);
            if (prop && g_mpvlib_class && g_mid_prop) {
                if (prop->format == MPV_FORMAT_STRING) {
                    const char *val_str = *reinterpret_cast<char**>(prop->data);
                    if (val_str) {
                        jstring jprop = env->NewStringUTF(prop->name ? prop->name : "");
                        jstring jval = env->NewStringUTF(val_str);
                        env->CallStaticVoidMethod(g_mpvlib_class, g_mid_prop, jprop, jval);
                        env->DeleteLocalRef(jprop);
                        env->DeleteLocalRef(jval);
                        if (env->ExceptionCheck()) {
                            env->ExceptionClear();
                        }
                    }
                }
            }
        } else if (event->event_id == MPV_EVENT_END_FILE || event->event_id == MPV_EVENT_FILE_LOADED || event->event_id == MPV_EVENT_PLAYBACK_RESTART) {
            if (g_mpvlib_class && g_mid_event) {
                env->CallStaticVoidMethod(g_mpvlib_class, g_mid_event, static_cast<jint>(event->event_id));
                if (env->ExceptionCheck()) {
                    env->ExceptionClear();
                }
            }
        }
    }

    g_vm->DetachCurrentThread();
    LOGI("MPV event loop thread stopped");
}

static void start_event_thread() {
    if (!g_event_running.exchange(true)) {
        if (g_event_thread.joinable()) {
            g_event_thread.join();
        }
        g_event_thread = std::thread(event_loop);
        LOGI("Started MPV event thread");
    }
}

static void stop_event_thread() {
    if (g_event_running.exchange(false)) {
        {
            std::lock_guard<std::mutex> lock(g_mpv_mutex);
            if (g_mpv) {
                mpv_wakeup(g_mpv);
            }
        }
        if (g_event_thread.joinable()) {
            g_event_thread.join();
        }
        LOGI("Stopped MPV event thread");
    }
}

extern "C" {

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_vm = vm;
    LOGI("JNI_OnLoad: registering JavaVM with FFmpeg");
    set_ffmpeg_java_vm(vm);

    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK) {
        jclass local_class = env->FindClass("moe/zako/viewer/core/player/MPVLib");
        if (local_class) {
            g_mpvlib_class = reinterpret_cast<jclass>(env->NewGlobalRef(local_class));
            g_mid_log = env->GetStaticMethodID(g_mpvlib_class, "onNativeLogMessage", "(Ljava/lang/String;ILjava/lang/String;)V");
            g_mid_prop = env->GetStaticMethodID(g_mpvlib_class, "onNativePropertyChange", "(Ljava/lang/String;Ljava/lang/String;)V");
            g_mid_event = env->GetStaticMethodID(g_mpvlib_class, "onNativeEvent", "(I)V");
            env->DeleteLocalRef(local_class);
            LOGI("JNI_OnLoad: MPVLib class and method IDs cached successfully");
        } else {
            LOGE("JNI_OnLoad: could not find MPVLib class");
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
            }
        }
    }
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL
JNI_OnUnload(JavaVM *vm, void *reserved) {
    LOGI("JNI_OnUnload invoked");
    stop_event_thread();
    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK) {
        if (g_mpvlib_class) {
            env->DeleteGlobalRef(g_mpvlib_class);
            g_mpvlib_class = nullptr;
        }
        if (g_app_ctx) {
            env->DeleteGlobalRef(g_app_ctx);
            g_app_ctx = nullptr;
        }
    }
    g_vm = nullptr;
}

JNIEXPORT void JNICALL
Java_moe_zako_viewer_core_player_MPVLib_create(JNIEnv *env, jobject thiz, jobject ctx) {
    LOGI("MPVLib native create invoked");
    stop_event_thread();

    std::lock_guard<std::mutex> lock(g_mpv_mutex);

    if (g_vm) {
        set_ffmpeg_java_vm(g_vm);
    }
    if (ctx) {
        set_ffmpeg_android_app_ctx(env, ctx);
    }

    if (g_mpv) {
        mpv_set_property_string(g_mpv, "vo", "null");
        int64_t wid = 0;
        mpv_set_property(g_mpv, "wid", MPV_FORMAT_INT64, &wid);
        mpv_terminate_destroy(g_mpv);
        g_mpv = nullptr;
    }
    if (g_surface) {
        env->DeleteGlobalRef(g_surface);
        g_surface = nullptr;
    }

    g_mpv = mpv_create();
    LOGI("mpv_create instance: %p", g_mpv);
    if (g_mpv) {
        // Set vo="null" during initialization before surface is attached to avoid "Missing surface pointer"
        mpv_set_option_string(g_mpv, "vo", "null");
        mpv_set_option_string(g_mpv, "msg-level", "all=warn");
        mpv_request_log_messages(g_mpv, "warn");
    }
}

JNIEXPORT void JNICALL
Java_moe_zako_viewer_core_player_MPVLib_init(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_mpv_mutex);
    LOGI("MPVLib native init invoked");
    if (g_mpv) {
        int err = mpv_initialize(g_mpv);
        LOGI("mpv_initialize result: %d", err);
        if (err >= 0) {
            start_event_thread();
        }
    }
}

JNIEXPORT void JNICALL
Java_moe_zako_viewer_core_player_MPVLib_destroy(JNIEnv *env, jobject thiz) {
    LOGI("MPVLib native destroy invoked");
    stop_event_thread();

    std::lock_guard<std::mutex> lock(g_mpv_mutex);
    if (g_mpv) {
        // First set vo="null", then wid=0, then destroy
        mpv_set_property_string(g_mpv, "vo", "null");
        int64_t wid = 0;
        mpv_set_property(g_mpv, "wid", MPV_FORMAT_INT64, &wid);
        mpv_terminate_destroy(g_mpv);
        g_mpv = nullptr;
    }
    if (g_surface) {
        env->DeleteGlobalRef(g_surface);
        g_surface = nullptr;
    }
}

JNIEXPORT void JNICALL
Java_moe_zako_viewer_core_player_MPVLib_attachSurface(JNIEnv *env, jobject thiz, jobject surface) {
    std::lock_guard<std::mutex> lock(g_mpv_mutex);
    LOGI("MPVLib native attachSurface invoked");
    if (!surface) return;

    // Deduplication: if surface is already attached, do not recreate GlobalRef or re-set wid
    if (g_surface && env->IsSameObject(g_surface, surface)) {
        LOGI("attachSurface: surface is already attached, re-ensuring vo=gpu");
        if (g_mpv) {
            mpv_set_property_string(g_mpv, "force-window", "yes");
            mpv_set_property_string(g_mpv, "vo", "gpu");
        }
        return;
    }

    if (g_surface) {
        if (g_mpv) {
            mpv_set_property_string(g_mpv, "vo", "null");
            int64_t zero_wid = 0;
            mpv_set_property(g_mpv, "wid", MPV_FORMAT_INT64, &zero_wid);
        }
        env->DeleteGlobalRef(g_surface);
        g_surface = nullptr;
    }
    g_surface = env->NewGlobalRef(surface);

    if (g_mpv && g_surface) {
        int64_t wid = static_cast<int64_t>(reinterpret_cast<intptr_t>(g_surface));
        int res_wid = mpv_set_property(g_mpv, "wid", MPV_FORMAT_INT64, &wid);
        int res_fw = mpv_set_property_string(g_mpv, "force-window", "yes");
        int res_vo = mpv_set_property_string(g_mpv, "vo", "gpu");
        LOGI("attachSurface configured: wid=%lld (res=%d), force-window=yes (res=%d), vo=gpu (res=%d)",
             static_cast<long long>(wid), res_wid, res_fw, res_vo);
    }
}

JNIEXPORT void JNICALL
Java_moe_zako_viewer_core_player_MPVLib_detachSurface(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_mpv_mutex);
    LOGI("MPVLib native detachSurface invoked");
    if (g_mpv) {
        // Order: First set vo="null" so VO tears down EGL surface cleanly, then set wid=0
        mpv_set_property_string(g_mpv, "vo", "null");
        int64_t wid = 0;
        mpv_set_property(g_mpv, "wid", MPV_FORMAT_INT64, &wid);
    }
    if (g_surface) {
        env->DeleteGlobalRef(g_surface);
        g_surface = nullptr;
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
