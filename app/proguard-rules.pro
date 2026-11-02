# mafari proguard rules
-dontwarn org.jetbrains.compose.**
-keep class dev.dsh.miuixbrowser.** { *; }


# Servo callbacks and JNI entry points must not be renamed or removed.
-keep class org.servo.servoview.** { *; }
