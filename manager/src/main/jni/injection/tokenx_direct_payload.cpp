// Live System Server APK loader. Entry contract matches the supplied JBCompanion injector.
#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <pthread.h>
#include <unistd.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>

namespace {
constexpr const char *TAG = "TokenX/DirectInjection";
constexpr const char *APK = "/data/system/tokenx_direct/payload.apk";
bool failed(JNIEnv *env, const char *step) {
    if (!env->ExceptionCheck()) return false;
    env->ExceptionDescribe();
    env->ExceptionClear();
    __android_log_print(ANDROID_LOG_ERROR, TAG, "DIRECT_INJECTION_JNI_FAILED step=%s", step);
    return true;
}
void *load(void *) {
    if (getuid() != 1000) return nullptr;
    char process[64]{};
    FILE *input = fopen("/proc/self/cmdline", "r");
    if (!input) return nullptr;
    size_t length = fread(process, 1, sizeof(process) - 1, input);
    fclose(input);
    if (!length || strcmp(process, "system_server")) return nullptr;
    long long request = 0;
    input = fopen("/data/system/tokenx_direct/request", "r");
    if (!input) return nullptr;
    int parsed = fscanf(input, "%lld", &request);
    fclose(input);
    if (parsed != 1 || request <= 0) return nullptr;
    using GetVMs = jint (*)(JavaVM **, jsize, jsize *);
    auto getVMs = reinterpret_cast<GetVMs>(dlsym(RTLD_DEFAULT, "JNI_GetCreatedJavaVMs"));
    void *art = nullptr;
    if (!getVMs) {
        art = dlopen("libart.so", RTLD_NOW | RTLD_NOLOAD);
        if (art) getVMs = reinterpret_cast<GetVMs>(dlsym(art, "JNI_GetCreatedJavaVMs"));
    }
    JavaVM *vm = nullptr;
    jsize count = 0;
    if (!getVMs || getVMs(&vm, 1, &count) != JNI_OK || !count || !vm) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "DIRECT_INJECTION_VM_UNAVAILABLE");
        if (art) dlclose(art);
        return nullptr;
    }
    JNIEnv *env = nullptr;
    if (vm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
        if (art) dlclose(art);
        return nullptr;
    }
    if (env->PushLocalFrame(32) == JNI_OK) {
        do {
            jclass base = env->FindClass("java/lang/ClassLoader");
            if (failed(env, "ClassLoader") || !base) break;
            jmethodID getParent = env->GetStaticMethodID(base, "getSystemClassLoader", "()Ljava/lang/ClassLoader;");
            if (failed(env, "parent method") || !getParent) break;
            jobject parent = env->CallStaticObjectMethod(base, getParent);
            if (failed(env, "parent") || !parent) break;
            jclass dex = env->FindClass("dalvik/system/DexClassLoader");
            if (failed(env, "DexClassLoader") || !dex) break;
            jmethodID ctor = env->GetMethodID(dex, "<init>", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/ClassLoader;)V");
            if (failed(env, "constructor") || !ctor) break;
            jstring apk = env->NewStringUTF(APK);
            jstring cache = env->NewStringUTF("/data/system/tokenx_direct/oat");
            if (failed(env, "paths") || !apk || !cache) break;
            jobject loader = env->NewObject(dex, ctor, apk, cache, nullptr, parent);
            if (failed(env, "APK loader") || !loader) break;
            jmethodID loadClass = env->GetMethodID(base, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
            if (failed(env, "loadClass method") || !loadClass) break;
            jstring name = env->NewStringUTF("rikka.shizuku.server.TokenXSystemServerInjection");
            if (failed(env, "entry name") || !name) break;
            auto entryClass = static_cast<jclass>(env->CallObjectMethod(loader, loadClass, name));
            if (failed(env, "entry class") || !entryClass) break;
            jmethodID start = env->GetStaticMethodID(entryClass, "startDirect", "(J)V");
            if (failed(env, "start method") || !start) break;
            env->CallStaticVoidMethod(entryClass, start, static_cast<jlong>(request));
            if (failed(env, "start")) break;
            __android_log_print(ANDROID_LOG_INFO, TAG, "DIRECT_INJECTION_ENTRY_STARTED uid=%d pid=%d", getuid(), getpid());
        } while (false);
        env->PopLocalFrame(nullptr);
    } else {
        failed(env, "local frame");
    }
    vm->DetachCurrentThread();
    if (art) dlclose(art);
    return nullptr;
}
}

extern "C" __attribute__((visibility("default"))) void entry() {
    // JNI work runs on an attached thread, never in the injector's stopped target thread.
    pthread_t worker;
    if (pthread_create(&worker, nullptr, load, nullptr) == 0) pthread_detach(worker);
    else __android_log_print(ANDROID_LOG_ERROR, TAG, "DIRECT_INJECTION_THREAD_FAILED");
}
