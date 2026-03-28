# Add project specific ProGuard rules here.
# Keep Apache POI classes
-keep class org.apache.poi.** { *; }
-keep class org.openxmlformats.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.openxmlformats.**

# Keep Room entities
-keep class com.ticketscanner.app.models.** { *; }
-keep class com.ticketscanner.app.database.** { *; }

# ZXing
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.** { *; }
