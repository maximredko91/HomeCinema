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
