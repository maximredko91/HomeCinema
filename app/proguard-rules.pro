# Room, Media3, Navigation-Compose, Coil, DataStore, and Kotlin coroutines all ship
# their own consumer ProGuard/R8 rules bundled in their .aar, so nothing extra is needed
# for them here. jcifs-ng doesn't, and does use reflection internally for auth/protocol
# negotiation - keep it whole to avoid a working-in-debug, broken-in-release surprise
# that would only show up once someone actually tries to connect over SMB on a release
# build.
-keep class jcifs.** { *; }
-dontwarn jcifs.**

# jcifs-ng references SLF4J for optional logging; no binding is bundled (SLF4J itself
# falls back to a no-op logger at runtime when none is found - this is expected, not
# a bug), so R8 just needs to be told not to worry about the missing classes.
-dontwarn org.slf4j.**

# androidx.security:security-crypto (EncryptedSharedPreferences, used for SMB source
# passwords) wraps Google Tink, which picks its crypto provider/implementation via
# reflection at runtime - R8's static analysis doesn't see those call sites, so members
# it can't prove are used are fair game to strip even though Tink needs them at runtime.
# This class of bug compiles fine and passes lint but only shows up the first time the
# affected code path actually runs on a release build - keep Tink whole rather than risk
# it again.
-keep class com.google.crypto.tink.** { *; }
-keep interface com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite {
    <fields>;
}
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
