# kotlinx.serialization: keep generated serializers for our @Serializable models
-keepclassmembers @kotlinx.serialization.Serializable class org.yb.secondwind.data.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class org.yb.secondwind.data.**$$serializer { *; }
