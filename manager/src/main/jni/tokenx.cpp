#include <android/log.h>
#include <sys/random.h>
#include <sys/types.h>
#include <unistd.h>

#include <cerrno>
#include <cstdio>
#include <cstring>

namespace {
constexpr const char* kVersion = "0.1";
constexpr size_t kTokenBytes = 32;

const char* uid_name(uid_t uid) {
    if (uid == 0) return "ROOT";
    if (uid == 1000) return "SYSTEM";
    if (uid == 2000) return "SHELL";
    return "APP";
}

void print_status() {
    const uid_t uid = getuid();
    const uid_t euid = geteuid();
    std::printf("TokenX Control %s\n", kVersion);
    std::printf("uid=%u (%s)\n", static_cast<unsigned>(uid), uid_name(uid));
    std::printf("euid=%u (%s)\n", static_cast<unsigned>(euid), uid_name(euid));
    std::printf("pid=%d ppid=%d\n", getpid(), getppid());
    std::printf("session_transport=binder-pending\n");
    std::printf("rish_fallback=enabled\n");
}

bool make_token(unsigned char* out, size_t size) {
    size_t done = 0;
    while (done < size) {
        const ssize_t n = getrandom(out + done, size - done, 0);
        if (n > 0) {
            done += static_cast<size_t>(n);
            continue;
        }
        if (n < 0 && errno == EINTR) continue;
        return false;
    }
    return true;
}

void print_token() {
    unsigned char token[kTokenBytes] = {};
    if (!make_token(token, sizeof(token))) {
        std::fprintf(stderr, "tokenx: getrandom failed: %s\n", std::strerror(errno));
        _exit(70);
    }
    // Development bootstrap only. Manager-side capability exchange will replace
    // printable tokens before privileged operations are enabled.
    std::printf("tx1.");
    for (size_t i = 0; i < sizeof(token); ++i) std::printf("%02x", token[i]);
    std::printf("\n");
}

int backend_probe(const char* requested, uid_t expected) {
    const uid_t actual = geteuid();
    std::printf("requested=%s expected_uid=%u actual_uid=%u actual=%s\n",
                requested, static_cast<unsigned>(expected),
                static_cast<unsigned>(actual), uid_name(actual));
    if (actual != expected) {
        std::fprintf(stderr,
                     "tokenx: backend handoff not active; use the existing rish route until Binder control is connected\n");
        return 77;
    }
    return 0;
}

void usage() {
    std::puts(
        "TokenX native control plane\n"
        "usage: tokenx <command>\n"
        "\n"
        "  status       show process/backend identity\n"
        "  token        create a one-shot bootstrap nonce (development only)\n"
        "  root         verify current process is UID 0\n"
        "  system       verify current process is UID 1000\n"
        "  shell        verify current process is UID 2000\n"
        "  sessions     show native session transport state\n"
        "  doctor       run local control-plane diagnostics\n"
        "  version      show control-plane version\n");
}
}  // namespace

int main(int argc, char** argv) {
    if (argc < 2) {
        usage();
        return 64;
    }
    const char* cmd = argv[1];
    if (std::strcmp(cmd, "status") == 0) {
        print_status();
        return 0;
    }
    if (std::strcmp(cmd, "token") == 0) {
        print_token();
        return 0;
    }
    if (std::strcmp(cmd, "root") == 0) return backend_probe("root", 0);
    if (std::strcmp(cmd, "system") == 0) return backend_probe("system", 1000);
    if (std::strcmp(cmd, "shell") == 0) return backend_probe("shell", 2000);
    if (std::strcmp(cmd, "sessions") == 0) {
        std::puts("native_sessions=0 transport=binder-pending rish_fallback=enabled");
        return 0;
    }
    if (std::strcmp(cmd, "doctor") == 0) {
        print_status();
        std::puts("entropy=getrandom");
        std::puts("capability_tokens=bootstrap-only");
        std::puts("privileged_dispatch=disabled-until-manager-auth");
        return 0;
    }
    if (std::strcmp(cmd, "version") == 0) {
        std::printf("tokenx-control %s\n", kVersion);
        return 0;
    }
    usage();
    return 64;
}
