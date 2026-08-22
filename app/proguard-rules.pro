# ==============================================================================
# OBSCURA VAULT - AGGRESSIVE R8 / PROGUARD SECURITY RULES
# ==============================================================================

# ------------------------------------------------------------------------------
# 1. AGGRESSIVE OBFUSCATION & SHRINKING DIRECTIVES
# Flatten and repackage internal code into empty root packages to obscure
# decompiled package architectures (ViewModel, Repositories, Crypto Managers).
# ------------------------------------------------------------------------------
-repackageclasses ''
-flattenpackagehierarchy ''
-allowaccessmodification
-overloadaggressively
-optimizations !code/simplification/arithmetic,!code/allocation/variable

# Remove all debug line numbers and source file names from stacktraces
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# Strip out remaining logging and runtime checks if any exist in third-party deps
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# ------------------------------------------------------------------------------
# 2. SQLCIPHER FOR ANDROID (JNI & NATIVE BINDINGS)
# Prevents UnsatisfiedLinkError crashes when loading libsqlcipher.so native binaries.
# ------------------------------------------------------------------------------
-keep class net.zetetic.database.sqlcipher.** { *; }
-keepclassmembers class net.zetetic.database.sqlcipher.** {
    native <methods>;
}
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.sqlcipher.**

# ------------------------------------------------------------------------------
# 3. ANDROID ROOM DATABASE & SQLCIPHER DRIVERS
# Prevents AbstractMethodError / NoSuchMethodException when Room instantiates
# DAOs, entities, generated _Impl classes, and TypeConverters at runtime.
# ------------------------------------------------------------------------------
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.migration.Migration { *; }
-keep class **_Impl { *; }

# Keep Room Type Converters and methods
-keep class * {
    @androidx.room.TypeConverter <methods>;
    @androidx.room.ProvidedTypeConverter <methods>;
}

# Prevent R8 from removing constructors called reflectively by Room
-keepclassmembers class * {
    @androidx.room.PrimaryKey *;
    @androidx.room.ColumnInfo *;
}

# ------------------------------------------------------------------------------
# 4. KOTLINX.SERIALIZATION / MOSHI (CUSTOM FIELDS & WEBAUTHN DTOs)
# Prevents property name mangling and MissingSerializerException crashes
# during JSON serialization of List<CustomField> in TypeConverters.
# ------------------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses, Signature

-keep @kotlinx.serialization.Serializable class * {
    *** Companion;
    public <fields>;
}

-keepclassmembers class * {
    public static *** Companion;
}

-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

-keepclassmembers @kotlinx.serialization.Serializable class * {
    kotlinx.serialization.internal.GeneratedSerializer Companion;
}

# Moshi rules if Moshi annotations are used
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep @com.squareup.moshi.JsonClass class * { *; }

# ------------------------------------------------------------------------------
# 5. ANDROID CREDENTIAL PROVIDER & AUTOFILL SERVICES
# Prevents ClassNotFoundException when the Android OS instantiates services
# declared in AndroidManifest.xml.
# ------------------------------------------------------------------------------
-keep public class * extends android.service.credentials.CredentialProviderService {
    public <init>();
    *;
}

-keep public class * extends android.service.autofill.AutofillService {
    public <init>();
    *;
}

# Keep the Credential Provider and Autofill Interceptor UI Activities
-keep public class com.example.** extends android.app.Activity { *; }

# ------------------------------------------------------------------------------
# 6. ANDROIDX AUTOFILL INLINE SUGGESTION UI (SLICES & IME PROCESS IPC)
# Prevents verification errors and Slice parsing failures in Gboard / IME.
# ------------------------------------------------------------------------------
-keep class androidx.autofill.inline.** { *; }
-keep interface androidx.autofill.inline.** { *; }
-keepclassmembers class androidx.autofill.inline.v1.InlineSuggestionUi$* { *; }

# ------------------------------------------------------------------------------
# 7. ANDROID BIOMETRIC PROMPT & CRYPTO LAYER
# Prevents obfuscation from stripping cryptographic algorithm specs and parameters.
# ------------------------------------------------------------------------------
-keep class androidx.biometric.** { *; }
-keep class java.security.spec.ECGenParameterSpec { *; }
-keep class java.security.interfaces.ECKey { *; }
-keep class java.security.interfaces.ECPrivateKey { *; }
-keep class java.security.interfaces.ECPublicKey { *; }

# ------------------------------------------------------------------------------
# 8. JETPACK COMPOSE RUNTIME & ANIMATION INTERNALS
# Prevents NoSuchMethodError during Compose state recomposition and layout measuring.
# ------------------------------------------------------------------------------
-keepclassmembers class * extends androidx.compose.ui.Modifier { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}
-dontwarn androidx.compose.**

# ------------------------------------------------------------------------------
# 9. GENERAL APPLICATION KEEP ANNOTATIONS
# Universal safeguard for any domain entity explicitly annotated with @Keep.
# ------------------------------------------------------------------------------
-keep @androidx.annotation.Keep class * { *; }
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <methods>;
}
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <fields>;
}
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <init>(...);
}

