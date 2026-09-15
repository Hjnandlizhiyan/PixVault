# onnxruntime 使用 JNI，类名与 native 方法名不可被混淆或裁剪
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
