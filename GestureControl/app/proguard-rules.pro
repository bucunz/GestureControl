# MediaPipe 通过 JNI 反射调用，必须保留
-keep class com.google.mediapipe.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
-keep class com.google.protobuf.** { *; }

# 无障碍服务不要被混淆掉
-keep class com.gesture.control.GestureAccessibilityService { *; }
-keep class com.gesture.control.CameraGestureService { *; }

-dontwarn java.awt.**
-dontwarn javax.**
