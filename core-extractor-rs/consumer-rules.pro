# ============================================================
# core-extractor-rs — Consumer ProGuard Rules
# ============================================================
# These rules travel with the library module and are automatically
# applied to any app that depends on :core-extractor-rs.

# Keep the JNI bridge class and all native methods.
# WHY: R8 cannot see JNI calls from Rust. Without this rule,
# the bridge class and its native methods may be stripped,
# causing UnsatisfiedLinkError at runtime.
-keep class dev.forgeotalab.nativebridge.NativeBridge {
    native <methods>;
    public *;
}

# Keep the System.loadLibrary target — the .so filename must match
# what the native code expects.
-keep class dev.forgeotalab.nativebridge.** { *; }
