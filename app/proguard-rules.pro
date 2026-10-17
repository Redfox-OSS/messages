# Library consumer rules (Hilt, Compose, Navigation 3, DataStore, Room, kotlinx.serialization,
# Coil, OkHttp) cover their own keeps; this file only adds release-wide optimisations.

# Strip verbose, debug and info logging from release builds (warnings and errors stay, so
# provider quirks and send failures still reach logcat). R8 drops the calls and the strings
# built for them.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Flatten obfuscated classes into one package: smaller dex (shorter names, fewer package strings).
-repackageclasses ''
-allowaccessmodification
