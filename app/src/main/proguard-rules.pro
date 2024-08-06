# Keep attributes for Android Framework classes
-keepattributes *Annotation*

# Keep public classes, fields, and methods
-keep public class * {
    public private *;
}

# Keep classes with specific annotations
-keep @org.altbeacon.beacon.BeaconConsumer class * { *; }
-keep class org.altbeacon.beacon.** { *; }

# Keep Firebase classes
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# If BuildConfig class is needed, use the correct applicationId
# Uncomment and modify the following line if needed
# -keep class com.example.myapplication2222.BuildConfig { *; }

# Keep any classes used in reflection
-keep class * extends android.app.Activity
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.content.ContentProvider
