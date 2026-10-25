# KovanLLM Proguard Rules for R8 Optimization
# Ensures 25%+ DEX code optimization without breaking runtime reflection, Compose, or ads.

# 1. Keep KovanLLM Data Models for JSON serialization
-keep class com.aselens.kovanllm.data.** { *; }
-keepclassmembers class com.aselens.kovanllm.data.** { *; }

# 2. Keep OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# 3. Keep Google Mobile Ads (AdMob) & UMP Consent SDK
-keep class com.google.android.gms.ads.** { *; }
-keep interface com.google.android.gms.ads.** { *; }
-keep class com.google.android.ump.** { *; }
-keep interface com.google.android.ump.** { *; }

# 4. Keep Google Play Services Code Scanner
-keep class com.google.android.gms.code.scanner.** { *; }

# 5. Keep Jetpack Compose & Coroutines
-keep class androidx.compose.** { *; }
-keep class kotlinx.coroutines.** { *; }

# 6. Basic optimization flags
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose
