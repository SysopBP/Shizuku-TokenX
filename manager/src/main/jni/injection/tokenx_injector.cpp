// TokenX KernelSU / Zygisk API v4 system_server-only injector.
// No ART hooks, UID changes, service replacement, or SELinux policy changes.
#include <stdint.h>
#include <sys/types.h>
#include <sys/time.h>
#include "zygisk.hpp"
#include <android/log.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <unistd.h>

namespace {
constexpr uint32_t MAGIC = 0x544b4e34;
constexpr uint32_t MAX_DEX = 16;
constexpr uint32_t MAX_BYTES = 128 * 1024 * 1024;
constexpr const char *DIR = "/data/adb/modules/tokenx_system_server/payload";
void logError(const char *message) { __android_log_write(ANDROID_LOG_ERROR, "TokenX/Injection", message); }
bool transfer(int fd, void *buffer, size_t size, bool writing) {
    auto *p = static_cast<unsigned char *>(buffer);
    while (size) {
        ssize_t n = writing ? send(fd, p, size, MSG_NOSIGNAL) : recv(fd, p, size, 0);
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) return false;
        size -= static_cast<size_t>(n); p += n;
    }
    return true;
}
int openPayload(const char *name) {
    char path[256];
    snprintf(path, sizeof(path), "%s/%s", DIR, name);
    int fd = open(path, O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    struct stat st{};
    if (fd < 0) return -1;
    if (fstat(fd, &st) || !S_ISREG(st.st_mode) || st.st_uid != 0 ||
            (st.st_mode & 0022) || st.st_size <= 0 || st.st_size > MAX_BYTES) {
        close(fd); return -1;
    }
    return fd;
}
void companion(int socket) {
    timeval timeout{5, 0};
    setsockopt(socket, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
    setsockopt(socket, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));
    uint32_t request = 0;
    if (!transfer(socket, &request, sizeof(request), false) || request != MAGIC) return;
    int config = openPayload("dex.count");
    if (config < 0) return;
    char text[16]{};
    ssize_t readCount = read(config, text, sizeof(text) - 1);
    close(config);
    if (readCount <= 0) return;
    char *end = nullptr;
    unsigned long parsed = strtoul(text, &end, 10);
    if (end == text || parsed == 0 || parsed > MAX_DEX || (*end && *end != '\n')) return;
    uint32_t count = static_cast<uint32_t>(parsed);
    uint32_t header[2]{MAGIC, count};
    if (!transfer(socket, header, sizeof(header), true)) return;
    uint64_t total = 0;
    for (uint32_t i = 1; i <= count; ++i) {
        char name[48];
        if (i == 1) snprintf(name, sizeof(name), "classes.dex");
        else snprintf(name, sizeof(name), "classes%u.dex", i);
        int fd = openPayload(name);
        if (fd < 0) return;
        struct stat st{};
        if (fstat(fd, &st)) { close(fd); return; }
        uint32_t size = static_cast<uint32_t>(st.st_size);
        total += size;
        if (size < 8 || total > MAX_BYTES || !transfer(socket, &size, sizeof(size), true)) {
            close(fd); return;
        }
        unsigned char buffer[65536];
        size_t remaining = size;
        while (remaining) {
            ssize_t n = read(fd, buffer, remaining < sizeof(buffer) ? remaining : sizeof(buffer));
            if (n < 0 && errno == EINTR) continue;
            if (n <= 0 || !transfer(socket, buffer, static_cast<size_t>(n), true)) { close(fd); return; }
            remaining -= static_cast<size_t>(n);
        }
        close(fd);
    }
}

class TokenXInjector : public zygisk::ModuleBase {
    zygisk::Api *api_ = nullptr;
    JNIEnv *env_ = nullptr;
    unsigned char *dex_[MAX_DEX]{};
    uint32_t sizes_[MAX_DEX]{};
    uint32_t count_ = 0;
    bool ready_ = false;
    void clear() {
        for (auto &p : dex_) { free(p); p = nullptr; }
        count_ = 0; ready_ = false;
    }
    bool exception() {
        if (!env_->ExceptionCheck()) return false;
        env_->ExceptionClear();
        logError("JNI injection failed; framework continues without TokenX injection");
        return true;
    }
public:
    void onLoad(zygisk::Api *api, JNIEnv *env) override { api_ = api; env_ = env; }
    void preAppSpecialize(zygisk::AppSpecializeArgs *) override {
        api_->setOption(zygisk::Option::DLCLOSE_MODULE_LIBRARY);
    }
    void preServerSpecialize(zygisk::ServerSpecializeArgs *args) override {
        if (args->uid != 1000) return;
        int socket = api_->connectCompanion();
        if (socket < 0) { logError("Root companion unavailable"); return; }
        timeval timeout{5, 0};
        setsockopt(socket, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
        setsockopt(socket, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));
        uint32_t request = MAGIC, header[2]{};
        bool ok = transfer(socket, &request, sizeof(request), true) &&
            transfer(socket, header, sizeof(header), false) &&
            header[0] == MAGIC && header[1] > 0 && header[1] <= MAX_DEX;
        uint64_t total = 0;
        if (ok) count_ = header[1];
        for (uint32_t i = 0; ok && i < count_; ++i) {
            ok = transfer(socket, &sizes_[i], sizeof(uint32_t), false);
            total += sizes_[i];
            ok = ok && sizes_[i] >= 8 && total <= MAX_BYTES;
            if (!ok) break;
            dex_[i] = static_cast<unsigned char *>(malloc(sizes_[i]));
            ok = dex_[i] && transfer(socket, dex_[i], sizes_[i], false) &&
                memcmp(dex_[i], "dex\n", 4) == 0;
        }
        close(socket);
        if (!ok) { clear(); logError("Payload rejected; no injection performed"); return; }
        ready_ = true;
    }
    void postServerSpecialize(const zygisk::ServerSpecializeArgs *) override {
        if (!ready_ || getuid() != 1000 || env_->PushLocalFrame(128) < 0) {
            exception(); clear(); return;
        }
        // Payload stays in native memory for the lifetime of the Dex class loader.
        // system_server never needs filesystem access to /data/adb.
        do {
            jclass buffers = env_->FindClass("java/nio/ByteBuffer");
            if (exception() || !buffers) break;
            jobjectArray array = env_->NewObjectArray(count_, buffers, nullptr);
            if (exception() || !array) break;
            for (uint32_t i = 0; i < count_; ++i) {
                jobject b = env_->NewDirectByteBuffer(dex_[i], sizes_[i]);
                if (exception() || !b) { ready_ = false; break; }
                env_->SetObjectArrayElement(array, i, b);
                env_->DeleteLocalRef(b);
                if (exception()) { ready_ = false; break; }
            }
            if (!ready_) break;
            jclass loaderClass = env_->FindClass("java/lang/ClassLoader");
            if (exception() || !loaderClass) break;
            jmethodID parentMethod = env_->GetStaticMethodID(loaderClass, "getSystemClassLoader", "()Ljava/lang/ClassLoader;");
            if (exception() || !parentMethod) break;
            jobject parent = env_->CallStaticObjectMethod(loaderClass, parentMethod);
            if (exception() || !parent) break;
            jclass memoryLoader = env_->FindClass("dalvik/system/InMemoryDexClassLoader");
            if (exception() || !memoryLoader) break;
            jmethodID ctor = env_->GetMethodID(memoryLoader, "<init>", "([Ljava/nio/ByteBuffer;Ljava/lang/ClassLoader;)V");
            if (exception() || !ctor) break;
            jobject loader = env_->NewObject(memoryLoader, ctor, array, parent);
            if (exception() || !loader) break;
            jmethodID load = env_->GetMethodID(loaderClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
            if (exception() || !load) break;
            jstring name = env_->NewStringUTF("rikka.shizuku.server.TokenXSystemServerInjection");
            if (exception() || !name) break;
            auto entry = static_cast<jclass>(env_->CallObjectMethod(loader, load, name));
            if (exception() || !entry) break;
            jmethodID start = env_->GetStaticMethodID(entry, "start", "()V");
            if (exception() || !start) break;
            env_->CallStaticVoidMethod(entry, start);
            if (exception()) break;
            __android_log_write(ANDROID_LOG_INFO, "TokenX/Injection", "SYSTEM_SERVER_INJECTION_STARTED");
        } while (false);
        // ART may retain any ByteBuffer already constructed even when later JNI calls fail.
        // Never free payload memory after constructing a loader.
        env_->PopLocalFrame(nullptr);
    }
};
}
REGISTER_ZYGISK_MODULE(TokenXInjector)
REGISTER_ZYGISK_COMPANION(companion)
