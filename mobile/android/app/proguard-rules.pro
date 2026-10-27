# Keep enough source information for useful release crash reports.
-keepattributes SourceFile,LineNumberTable

# ONNX Runtime's JNI layer resolves its Java classes, fields and constructors by
# hardcoded name from native code (FindClass/GetMethodID/GetFieldID). R8 renaming
# or stripping any of them turns into `java_class == null` and a SIGABRT inside
# convertToTensorInfo the first time a model runs, so the whole package must
# survive shrinking untouched.
-keep class ai.onnxruntime.** { *; }
-keep class ai.onnxruntime.providers.** { *; }
-dontwarn ai.onnxruntime.**

# Listening Party peers bind these natives by name and are called back from C++.
-keep class dev.sfg.numa.mobile.social.PartyPeerNative { native <methods>; }
-keep interface dev.sfg.numa.mobile.social.PartyPeerNative$Listener { *; }
-keep class * implements dev.sfg.numa.mobile.social.PartyPeerNative$Listener {
    void onSignal(java.lang.String);
    void onOpen();
    void onText(java.lang.String);
    void onClosed(java.lang.String);
}

# The shared adaptive-mix library binds these natives by name and calls `pair` back from Rust.
-keep class dev.sfg.numa.mobile.playback.smart.MixNative { native <methods>; }
-keep interface dev.sfg.numa.mobile.playback.smart.MixNative$BestMixPairs { *; }
-keep class * implements dev.sfg.numa.mobile.playback.smart.MixNative$BestMixPairs { *; }
# The Discord shim binds these natives by name and calls onStatus back from C++.
-keep class dev.sfg.numa.mobile.discord.DiscordNative { native <methods>; public static void onStatus(java.lang.String, boolean); }

# Numa Connect's native core binds these by name and calls the listener back from C++.
-keep class dev.sfg.numa.mobile.connect.ConnectNative { native <methods>; }
-keep interface dev.sfg.numa.mobile.connect.ConnectNative$Listener { *; }
-keep class * implements dev.sfg.numa.mobile.connect.ConnectNative$Listener {
    void onHubSend(java.lang.String);
    void onEvent(java.lang.String);
    void onData(java.lang.String, java.lang.String, byte[]);
}
# The QuickJS provider host calls these callbacks by name from native code.
-keep class dev.sfg.numa.mobile.provider.ProviderHost { *; }
-keep class * extends dev.sfg.numa.mobile.provider.ProviderHost { *; }
-keep class dev.sfg.numa.mobile.provider.ProviderNative { native <methods>; }

# Strip verbose/debug logging from release; several sit on playback and UI hot paths.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

