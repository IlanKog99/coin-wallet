# kotlinx.serialization: keep generated serializers of our @Serializable classes (files + nav routes).
-keepclassmembers @kotlinx.serialization.Serializable class com.ilankog.coinwallet.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ilankog.coinwallet.**$$serializer { *; }
