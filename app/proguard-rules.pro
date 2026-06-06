# ═══════════════════════════════════════════════════════════════════════════
# Regras ProGuard / R8 para release
# ═══════════════════════════════════════════════════════════════════════════

# ── Modelos de dados ──────────────────────────────────────────────────────
# Tudo em data.** é serializado via Gson (campos lidos por reflexão a partir
# do nome). Ofuscar/remover quebraria a (de)serialização de saves e do
# snapshot estático. Mantém classes + membros intactos.
-keep class com.cblol.scout.data.** { *; }

# Domínio que também é (de)serializado em GameState (CoachBonuses, RoleAssignment,
# OfferStatus, etc.). Mantém para garantir compatibilidade dos saves.
-keep class com.cblol.scout.domain.** { *; }

# ── Gson ──────────────────────────────────────────────────────────────────
# Mantém atributos genéricos e anotações usados pelo Gson em runtime.
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Campos anotados com @SerializedName não podem ser renomeados.
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# TypeToken do Gson (usado para desserializar List<T>/Map<K,V>).
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# ── Realm Kotlin ──────────────────────────────────────────────────────────
# O Realm Kotlin SDK já inclui suas próprias regras consumer; estas são
# reforços defensivos para os modelos de persistência do app.
-keep class io.realm.kotlin.** { *; }
-keep class com.cblol.scout.data.realm.** { *; }
-dontwarn io.realm.kotlin.**

# ── Koin ──────────────────────────────────────────────────────────────────
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# ── Glide ─────────────────────────────────────────────────────────────────
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# ── Kotlin / Coroutines ───────────────────────────────────────────────────
-keepclassmembers class kotlin.Metadata { public <methods>; }
-dontwarn kotlinx.coroutines.**

# ── Enums ─────────────────────────────────────────────────────────────────
# Enums serializados pelo Gson pelo nome (OfferStatus, PickBanPhase, etc.).
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Modelos parceláveis / view binding ────────────────────────────────────
# View binding gera classes *Binding; mantidas por segurança.
-keep class com.cblol.scout.databinding.** { *; }
