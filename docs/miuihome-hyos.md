# Rust/Flutter MiuiHome support

The module contains an arm64 LSPosed `native_init` entry. It relies on LSPosed's
HYOS injection support; it does not install a standalone Zygisk module or modify
system properties. Enable this module for both `android` (system framework) and
`com.miui.home`.

Install the complete module APK, enable both scopes, and reboot to load the
system_server bridge and a fresh HYOS native generation. Open the module's
settings once after upgrading to copy existing launcher options to the new
bridge; subsequent changes use the same three switches as the Java launcher.

The initial native profile is deliberately limited to
`RELEASE-8.01.02.7751-260904-10100945-R`. Both APK-contained ELF files must match
their SHA-256 hashes and all eight hook sites must match their fingerprints
before any Dart hook is installed. Unknown versions get no native hooks; the
system_server prelaunch filter is independent of the native profile.
The native backend chains the runtime's `madvise` import and protects only this
module's hook pages. It respects the device page size, including 16 KiB devices.

The old Java launcher still uses `MiuiHomeHandler`. Rust packages are identified
by their lack of DEX code rather than by SDK version alone.

## Behavior

- Disabling prelaunch filters only MiuiHome's `AMS.preStartProcess` calls in
  system_server. Normal icon launches and other callers remain unaffected.
- A drag must last at least 700 ms before release. The release freezes its
  duration; dismissal animation time is excluded. Only the matching task's real
  cleanup commits the force-stop. Cancellation, replacement, another user, and
  stale cleanup cannot reuse the mark. The HYOS version uses a “停止进程” Toast
  for the armed indication; it does not replace the Flutter card title.
- Holding the recents menu's information button for 500 ms opens AOSP app info
  in a normal task stack. Its ordinary short press remains unchanged. This is
  the menu information button, not the task header's settings button. Only the
  exact void onPressed closure is consumed; asynchronous Dart entry points are
  never replaced with null-returning stubs.

New MiuiHome does not hold `FORCE_STOP_PACKAGES`. Native code copies task IDs,
users and package names on the owning Dart thread, and delegates privileged
actions to the existing system_server module. No Dart object pointer is retained
across events, threads, or a GC. The observers preserve registers, vector state,
flags and Dart's X15 stack, and make no VM calls or allocations.

The private bridge uses the existing PackageManager Binder for read-only boolean
queries in `io.github.a13e300.myinjector.miuihome.*`. It uses a namespaced marker
Intent through `AMS.preStartProcess` for commands. Caller UIDs are checked against
the installed launcher's UID for the caller's Android user. The marker package
is intentionally nonexistent so unloading the bridge cannot turn a command into
an ordinary target-app prelaunch. No public feature or normal prelaunch request
is changed by this protocol.

Configuration still uses MiuiHomeConfig protobuf fields 1–3. The settings UI
sends explicit authenticated broadcasts to the Java launcher and system_server;
the native worker refreshes configuration within about one second. The runtime
worker sleeps on eventfd when idle. It never performs Binder work on Dart's UI
thread. Disabling or reloading the Java bridge causes native features to stop
when the next configuration query sees the missing bridge.

## Build and offline checks

```sh
./gradlew :app:assembleDebug
python3 scripts/check_miuihome_native.py \
  --launcher /path/to/MiuiHome-rs.apk \
  --module app/build/outputs/apk/debug/MyInjector-<version>-debug.apk
```

The checker tests timing, cancellation, replacement, Android-user identity,
one-shot consumption and the streaming SHA implementation, verifies the launcher
profile, and checks native metadata plus APK/ELF 16 KiB alignment. It never
connects to or changes a device.

Native library changes require a fresh HYOS native generation; a Java hot reload
does not replace an already-mapped native library. Install the complete module
APK through PackageManager. Do not overwrite mapped `.so` files. Retain the
previous complete module APK for rollback.

On-device validation should cover native_init/profile readiness, threshold
boundaries, cancellation, both card layouts, short/long information-button
presses, actual stopped state and secondary users. Offline checks do not prove
those runtime behaviors. Native diagnostics use the `MyInjectorMiuiHome` and
`MyInjectorHyosMadvise` log tags.
