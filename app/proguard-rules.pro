# ============================================================
# Forge OTA Lab — ProGuard / R8 Rules
# ============================================================
# Each rule traces to a PRD requirement or technical necessity.

# ============================================================
# JNI native methods — keep the bridge class and its methods
# ============================================================
-keep class dev.forgeotalab.nativebridge.NativeBridge {
    native <methods>;
    *;
}

# ============================================================
# Room entities — keep annotated classes for reflection
# ============================================================
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }

# ============================================================
# Hilt — generated components
# ============================================================
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ============================================================
# Protobuf-lite — uses reflection for field access
# PRD: protobuf-javalite for DeltaArchiveManifest parsing
# ============================================================
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite$Builder { *; }

# ============================================================
# kotlinx.serialization — keep serializer classes
# ============================================================
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}

-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class dev.forgeotalab.**$$serializer { *; }

-keepclassmembers class dev.forgeotalab.** {
    *** Companion;
}

-keepclasseswithmembers class dev.forgeotalab.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Same rules for shared-contracts module
-keep,includedescriptorclasses class dev.forgeotalab.contracts.**$$serializer { *; }

-keepclassmembers class dev.forgeotalab.contracts.** {
    *** Companion;
}

-keepclasseswithmembers class dev.forgeotalab.contracts.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ============================================================
# Coroutines — debugging support
# ============================================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ============================================================
# BouncyCastle — Ed25519 adapter manifest verification
# WHY: BouncyCastle uses reflection for cipher/signature provider
# lookup. Stripping these breaks ManifestSignatureVerifier.
# ============================================================
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ============================================================
# OkHttp — adapter manifest refresh HTTP client
# ============================================================
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keep class okhttp3.internal.publicsuffix.PublicSuffixDatabase { *; }

# ============================================================
# WorkManager + Hilt workers — @HiltWorker assisted inject
# WHY: R8 can strip AssistedInject constructors. Workers must
# be discoverable by HiltWorkerFactory at runtime.
# ============================================================
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ============================================================
# Enums — keep values() and valueOf() for serialization
# WHY: Enums cross JNI as JSON, kotlinx.serialization uses
# valueOf() reflectively. R8 full mode can merge/strip these.
# ============================================================
-keepclassmembers enum dev.forgeotalab.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers enum dev.forgeotalab.contracts.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================================
# Crashlytics — preserve line numbers for deobfuscation
# WHY: Without SourceFile and LineNumberTable, crash reports
# show obfuscated names with no line context.
# ============================================================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============================================================
# Firebase — prevent stripping of Firebase init providers
# ============================================================
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ============================================================
# DataStore — generated Preferences
# ============================================================
-keep class androidx.datastore.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
