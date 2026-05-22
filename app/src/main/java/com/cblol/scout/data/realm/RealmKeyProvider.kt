package com.cblol.scout.data.realm

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Gerencia a **chave de criptografia de 64 bytes** exigida pelo Realm para
 * cifrar o arquivo do banco com AES-256.
 *
 * **Estratégia de proteção (envelope encryption):**
 *  1. Uma chave-mestra AES é gerada e mantida dentro do **Android Keystore**
 *     (hardware-backed quando disponível), de onde os bytes nunca saem.
 *  2. A chave de 64 bytes do Realm é gerada aleatoriamente uma única vez,
 *     cifrada com a chave-mestra (AES/GCM) e o blob cifrado é guardado em
 *     SharedPreferences.
 *  3. Em cada abertura do app, deciframos o blob com a chave do Keystore para
 *     recuperar os 64 bytes em memória e passá-los ao Realm.
 *
 * Assim a chave do Realm nunca é persistida em claro, e mesmo um dump das
 * SharedPreferences não revela a chave sem acesso ao Keystore do dispositivo.
 *
 * **SOLID:** SRP — esta classe só cuida da chave; abrir/seed do Realm é do
 * [RealmProvider]. Sem dependências do domínio.
 */
class RealmKeyProvider(private val context: Context) {

    /**
     * Retorna a chave de 64 bytes do Realm, criando-a (e protegendo-a) na
     * primeira chamada. Idempotente: chamadas seguintes devolvem a mesma chave.
     */
    fun getOrCreateKey(): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val storedB64 = prefs.getString(KEY_ENCRYPTED_REALM_KEY, null)
        val storedIvB64 = prefs.getString(KEY_IV, null)

        if (storedB64 != null && storedIvB64 != null) {
            val encrypted = Base64.decode(storedB64, Base64.NO_WRAP)
            val iv = Base64.decode(storedIvB64, Base64.NO_WRAP)
            return decrypt(encrypted, iv)
        }

        // Primeira execução: gera a chave de 64 bytes do Realm e a protege.
        val realmKey = ByteArray(REALM_KEY_SIZE).also { SecureRandom().nextBytes(it) }
        val (encrypted, iv) = encrypt(realmKey)
        prefs.edit()
            .putString(KEY_ENCRYPTED_REALM_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .apply()
        return realmKey
    }

    // ── Keystore / AES-GCM ──────────────────────────────────────────────

    private fun masterKey(): SecretKey {
        val keystore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keystore.getEntry(MASTER_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)
            ?.let { return it.secretKey }

        // Gera a chave-mestra no Keystore se ainda não existe.
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val encrypted = cipher.doFinal(plain)
        return encrypted to cipher.iv
    }

    private fun decrypt(encrypted: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(encrypted)
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "cblol_realm_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128

        private const val PREFS = "cblol_realm_keys"
        private const val KEY_ENCRYPTED_REALM_KEY = "encrypted_realm_key"
        private const val KEY_IV = "realm_key_iv"

        /** O Realm exige exatamente 64 bytes para a criptografia AES-256. */
        const val REALM_KEY_SIZE = 64
    }
}
