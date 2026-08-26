# Maximus VPN — R8 / ProGuard rules (v2.0)

# --- Compose / AndroidX (mostly shipped with their own rules) ---
-dontwarn org.slf4j.**
-dontwarn javax.naming.**

# --- Kotlinx serialization / reflection safety for config JSON ---
# Config JSON is built with org.json at runtime; no model reflection needed,
# but keep enum values used via valueOf() lookups.
-keepclassmembers enum com.drfxai.maximusvpn.data.model.** {
    public static final **[] values();
    public static final ** valueOf(java.lang.String);
}
-keepclassmembers enum com.drfxai.maximusvpn.data.repository.ServerSortOption {
    public static final **[] values();
    public static final ** valueOf(java.lang.String);
}

# --- Room entities: field names must survive for query verification & migrations ---
-keep class com.drfxai.maximusvpn.data.database.** { *; }
-keep class com.drfxai.maximusvpn.subscription.SubscriptionEntity { *; }

# --- OkHttp / Okio ---
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- ZXing (pure java, but keep hints enums intact) ---
-keep class com.google.zxing.** { *; }

# --- CameraX is AndroidX-managed; nothing extra required ---

# Remove debug logging in release builds of our own classes
-assumenosideeffects class com.drfxai.maximusvpn.xray.XrayLogManager {
    public static void appendLog(...);
}
