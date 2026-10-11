#include <android/log.h>
#include <fcntl.h>
#include <sys/types.h>
#include <unistd.h>

#include <errno.h>
#include <stdio.h>
#include <string.h>

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
    printf("TokenX Control %s\n", kVersion);
    printf("uid=%u (%s)\n", static_cast<unsigned>(uid), uid_name(uid));
    printf("euid=%u (%s)\n", static_cast<unsigned>(euid), uid_name(euid));
    printf("pid=%d ppid=%d\n", getpid(), getppid());
    printf("session_transport=binder-pending\n");
    printf("rish_fallback=enabled\n");
}

bool make_token(unsigned char* out, size_t size) {
    const int fd = open("/dev/urandom", O_RDONLY | O_CLOEXEC);
    if (fd < 0) return false;

    size_t done = 0;
    while (done < size) {
        const ssize_t n = read(fd, out + done, size - done);
        if (n > 0) {
            done += static_cast<size_t>(n);
            continue;
        }
        if (n < 0 && errno == EINTR) continue;
        const int saved_errno = (n == 0) ? EIO : errno;
        close(fd);
        errno = saved_errno;
        return false;
    }

    if (close(fd) != 0) return false;
    return true;
}

void print_token() {
    unsigned char token[kTokenBytes] = {};
    if (!make_token(token, sizeof(token))) {
        fprintf(stderr, "tokenx: entropy read failed: %s\n", strerror(errno));
        _exit(70);
    }
    // Development bootstrap only. Manager-side capability exchange will replace
    // printable tokens before privileged operations are enabled.
    printf("tx1.");
    for (size_t i = 0; i < sizeof(token); ++i) printf("%02x", token[i]);
    printf("\n");
}

int backend_probe(const char* requested, uid_t expected) {
    const uid_t actual = geteuid();
    printf("requested=%s expected_uid=%u actual_uid=%u actual=%s\n",
                requested, static_cast<unsigned>(expected),
                static_cast<unsigned>(actual), uid_name(actual));
    if (actual != expected) {
        fprintf(stderr,
                     "tokenx: backend handoff not active; use the existing rish route until Binder control is connected\n");
        return 77;
    }
    return 0;
}

void usage() {
    puts(
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
    if (strcmp(cmd, "status") == 0) {
        print_status();
        return 0;
    }
    if (strcmp(cmd, "token") == 0) {
        print_token();
        return 0;
    }
    if (strcmp(cmd, "root") == 0) return backend_probe("root", 0);
    if (strcmp(cmd, "system") == 0) return backend_probe("system", 1000);
    if (strcmp(cmd, "shell") == 0) return backend_probe("shell", 2000);
    if (strcmp(cmd, "sessions") == 0) {
        puts("native_sessions=0 transport=binder-pending rish_fallback=enabled");
        return 0;
    }
    if (strcmp(cmd, "doctor") == 0) {
        print_status();
        puts("entropy=/dev/urandom");
        puts("capability_tokens=bootstrap-only");
        puts("privileged_dispatch=disabled-until-manager-auth");
        return 0;
    }
    if (strcmp(cmd, "version") == 0) {
        printf("tokenx-control %s\n", kVersion);
        return 0;
    }
    usage();
    return 64;
}
