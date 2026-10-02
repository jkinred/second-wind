# kotlinx.serialization: keep generated serializers for our @Serializable models
-keepclassmembers @kotlinx.serialization.Serializable class io.github.jkinred.secondwind.data.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.jkinred.secondwind.data.**$$serializer { *; }
