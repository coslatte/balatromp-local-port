# Add project specific ProGuard rules here.
# Keep Ktor engine classes
-keep class io.ktor.** { *; }
-keep class org.slf4j.** { *; }
-dontwarn org.slf4j.**
-dontwarn org.slf4j.impl.**
