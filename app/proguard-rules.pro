# Minimal R8 keep rules for vibemusic-native (review item 10 evaluation).
# Everything else relies on AGP defaults (proguard-android-optimize.txt keeps
# *Annotation* + manifest components) and on libraries that ship their own
# consumer rules (OkHttp, Media3, Coil, DataStore-Preferences, Compose).

# Retrofit builds a runtime dynamic proxy for VibeService: R8 must keep the
# interface + its methods + the retrofit2 annotations they carry, otherwise
# the proxy sees stripped methods and every API call breaks (search, login,
# playlists, favorites, lyrics, update-check download all flow through it).
-keep interface com.cyk666.vibemusic.VibeService { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes *Annotation*

# Manifest-declared entry points (auto-kept by AGP, pinned explicitly so no
# future manifest refactor can silently expose them to shrinking): playback
# service (MediaSession + notification controls) and launcher activity
# (notification tap target, FileProvider install intent for the updater).
-keep class com.cyk666.vibemusic.PlaybackService { *; }
-keep class com.cyk666.vibemusic.MainActivity { *; }
