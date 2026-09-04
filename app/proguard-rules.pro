# ProGuard / R8 Optimization Rules for TVGrip

# BouncyCastle is loaded reflectively for client-certificate generation.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.jce.provider.BouncyCastleProvider { *; }

-dontwarn com.google.firebase.**
-dontwarn retrofit2.**
-dontwarn okhttp3.**

# Room Database Optimization & Rules
-keep class androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep Data Models
-keep class com.example.core.model.** { *; }
-keep class com.example.core.data.local.** { *; }

