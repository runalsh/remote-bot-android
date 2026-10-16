# RemoteBot Proguard Rules
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}

