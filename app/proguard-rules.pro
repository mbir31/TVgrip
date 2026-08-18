# ProGuard / R8 Optimization Rules for TVGrip

-dontwarn com.google.firebase.**
-dontwarn retrofit2.**
-dontwarn okhttp3.**

# Room Database Optimization & Rules
-keep class androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep Data Models
-keep class com.example.core.model.** { *; }
-keep class com.example.core.data.local.** { *; }

