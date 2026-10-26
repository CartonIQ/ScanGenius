# Proguard rules for ScanGenius
-keep class com.journeyapps.barcodescanner.** { *; }
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Preserve Room entities and DAOs
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep attributes
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
