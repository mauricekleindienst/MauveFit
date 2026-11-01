# kotlinx.serialization ships its own R8 rules; OkHttp probes optional TLS providers.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Glance pulls in WorkManager, whose Room database is created by reflection; keep its generated
# implementation so the release build doesn't crash on start.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
