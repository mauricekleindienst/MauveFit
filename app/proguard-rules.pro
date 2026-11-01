# kotlinx.serialization ships its own R8 rules; OkHttp probes optional TLS providers.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Glance pulls in WorkManager, whose Room database is created by reflection; keep its generated
# implementation so the release build doesn't crash on start.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }

# ML Kit's code scanner wires its components by reflection; keep them whole for release builds.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_code_scanner.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_** { *; }

# WorkManager creates its input mergers and workers by reflection, and the Glance widget renders
# inside such a worker. Without these the widget hangs on its loading spinner in release builds.
-keep class * extends androidx.work.InputMerger { <init>(); }
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }
-keep class androidx.glance.** { *; }
