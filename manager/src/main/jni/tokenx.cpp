#include <android/log.h>
#include <sys/random.h>
#include <sys/types.h>
#include <unistd.h>

#include <array>
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <string>

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

bool make_token(std::array<unsigned char, kTokenBytes>& out) {
    size_t done = 0;
    while (done < out.size()) {
        const ssize_t n = getrandom(out.data() + done, out.size() - done, 0);
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
    std::array<unsigned char, kTokenBytes> token{};
    if (!make_token(token)) {
        std::fprintf(stderr, "tokenx: getrandom failed: %s\n", std::strerror(errno));
        _exit(70);
    }
    // Development bootstrap only. Manager-side capability exchange will replace
    // printable tokens before privileged operations are enabled.
    std::printf("tx1.");
    for (unsigned char b : token) std::printf("%02x", b);
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
    const std::string cmd(argv[1]);
    if (cmd == "status") {
        print_status();
        return 0;
    }
    if (cmd == "token") {
        print_token();
        return 0;
    }
    if (cmd == "root") return backend_probe("root", 0);
    if (cmd == "system") return backend_probe("system", 1000);
    if (cmd == "shell") return backend_probe("shell", 2000);
    if (cmd == "sessions") {
        std::puts("native_sessions=0 transport=binder-pending rish_fallback=enabled");
        return 0;
    }
    if (cmd == "doctor") {
        print_status();
        std::puts("entropy=getrandom");
        std::puts("capability_tokens=bootstrap-only");
        std::puts("privileged_dispatch=disabled-until-manager-auth");
        return 0;
    }
    if (cmd == "version") {
        std::printf("tokenx-control %s\n", kVersion);
        return 0;
    }
    usage();
    return 64;
}
