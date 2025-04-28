-allowaccessmodification
-overloadaggressively

-keep class io.github.a13e300.myinjector.Entry

-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void check*(...);
    public static void throw*(...);
}

-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
  <fields>;
}
