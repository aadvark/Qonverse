# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class com.kithandkeen.qonverse.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.kithandkeen.qonverse.**$$serializer { *; }
-keepclassmembers class com.kithandkeen.qonverse.** { *** Companion; }
