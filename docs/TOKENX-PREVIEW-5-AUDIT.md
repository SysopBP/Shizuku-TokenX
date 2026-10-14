# TokenX Preview 5 — wireless reliability audit

Based on successful Build 715 (`a25a68e3`), retaining the current independent `com.sysopbp.tokenx` identity, manager UI, licensing, and Root/System/Shell routing. This is a focused source review, not a claim that every TokenX feature or Samsung firmware path is verified.

## Confirmed issues fixed

- Discovery start/stop callbacks and worker requests used shared mutable state across threads. Restarting while NSD stopped could leave the next search inactive. Requests and callbacks now share the main looper; a restart waits for the stop callback. Start/resolve retries are bounded, and stale resolution callbacks are discarded by session.
- Discovery attempted a server-socket bind as a port test, treating any I/O failure as proof of a listener. The app now checks local-interface ownership and valid port range, then lets the authenticated connection verify the endpoint.
- Wireless authorization waits could cancel their only timeout and block the unique worker indefinitely. The authorization wait now has a 130-second deadline.
- ADB command reads became unlimited after handshake. They now have a 30-second inactivity timeout, allowing worker failure/retry instead of a permanent stuck start. TCP readiness probes have a one-second connect deadline.
- ADB payload size was allocated before validating the header. Magic and length are now checked before reading/allocating the body. Socket regression tests cover malformed negative and oversized payloads.
- Pairing restart could reuse a notification containing an old port. Restarts clear it and rediscover. Notification replies require a six-digit code and valid port; result publication runs on the main thread and is suppressed after service destruction.

## Validation

CI compiles the APK, runs manager unit tests, validates TokenX identity/assets and rish routing. These checks do not prove six-digit pairing, mDNS lifecycle or Samsung reconnect behavior on a phone. Root/System backend code is unchanged by these fixes.

## Phone acceptance checks

1. Use the current TokenX installation (`com.sysopbp.tokenx`), grant notifications, and pair using the current pairing dialog's port/code. The pairing port differs from the connect port.
2. Verify wireless start delivers Shell UID 2000; separately verify explicit Root UID 0 and System UID 1000. Export a fresh matching rish/dex pair.
3. Repeat pairing search stop/start rapidly, close/reopen the Settings pairing dialog, and retry after an invalid code. Confirm the notification says Enter code and uses a newly discovered port.
4. Toggle wireless debugging and Wi-Fi off/on. Confirm startup terminates with an actionable status rather than remaining stuck; then reconnect. Test a locked-screen start followed by unlock.
5. Test app process restart and a device reboot. Collect TokenX diagnostics in Download if discovery, pairing or connection still fails.

## Remaining limits

Wireless debugging still requires Android's authorization, an available local interface and a live adbd endpoint. A successful source fix cannot establish the cause of an unprovided device log. This draft preview is for device validation before public release.
