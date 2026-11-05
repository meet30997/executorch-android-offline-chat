# ExecuTorch Core & JNI Bindings
-keep @com.facebook.jni.annotations.DoNotStrip class org.pytorch.executorch.** { *; }
-keepclassmembers class org.pytorch.executorch.** {
    @com.facebook.jni.annotations.DoNotStrip *;
}
-keepclasseswithmembers class org.pytorch.executorch.** {
    native <methods>;
}
-keepclassmembers class org.pytorch.executorch.** {
    com.facebook.jni.HybridData *;
}
-keep class org.pytorch.executorch.ExecutorchRuntimeException { *; }
-keep class org.pytorch.executorch.ExecutorchRuntimeException$* { *; }
-keep interface org.pytorch.executorch.extension.llm.LlmCallback { *; }
-keep class org.pytorch.executorch.extension.llm.LlmModule { *; }
-keep class org.pytorch.executorch.extension.llm.LlmModuleConfig { *; }
-keep class org.pytorch.executorch.extension.llm.LlmModuleConfig$Builder { *; }

# Facebook JNI & SoLoader
-keep class com.facebook.jni.** { *; }
-keep class com.facebook.soloader.** { *; }
-dontwarn com.facebook.soloader.**
-dontwarn javax.annotation.**

