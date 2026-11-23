package tv.own.owntv.mobile.ui.screens.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Explicit opt-in Dispatcharr administrator connection. Credentials are stored only on this
 * Android installation, protected with an Android Keystore AES-GCM key. They are never
 * included in a playlist, log, backup or build configuration.
 *
 * The HTTPS origin must be entered by the user: never derive the admin API target from
 * an untrusted IPTV playlist, Xtream redirect or channel URL.
 */
class DispatcharrConnectionStore private constructor(context: Context) {
    data class Connection(val origin: String, val apiKey: String)

    private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _connection = MutableStateFlow(decryptSaved())
    val connection: StateFlow<Connection?> = _connection

    fun save(origin: String, apiKey: String) {
        val normalized = normalizeOrigin(origin)
        require(apiKey.isNotBlank())
        val plain = JSONObject().put("origin", normalized).put("key", apiKey.trim()).toString()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encoded = Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        check(preferences.edit().putString(ENTRY, encoded).commit())
        _connection.value = Connection(normalized, apiKey.trim())
    }

    fun clear() {
        check(preferences.edit().remove(ENTRY).commit())
        _connection.value = null
    }

    private fun decryptSaved(): Connection? = runCatching {
        val saved = preferences.getString(ENTRY, null) ?: return null
        val bytes = Base64.decode(saved, Base64.DEFAULT)
        require(bytes.size > IV_SIZE)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(0, IV_SIZE)))
        val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(IV_SIZE, bytes.size)), Charsets.UTF_8))
        Connection(normalizeOrigin(json.getString("origin")), json.getString("key"))
    }.getOrNull()

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
            generateKey()
        }
    }

    companion object {
        private const val PREFS = "dispatcharr_private_connection"
        private const val ENTRY = "sealed_connection"
        private const val KEY_ALIAS = "owntv_mobile_dispatcharr_connection_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        @Volatile private var instance: DispatcharrConnectionStore? = null

        fun get(context: Context): DispatcharrConnectionStore = instance ?: synchronized(this) {
            instance ?: DispatcharrConnectionStore(context.applicationContext).also { instance = it }
        }

        fun normalizeOrigin(input: String): String {
            val uri = URI(input.trim().trimEnd('/'))
            require(uri.scheme.equals("https", ignoreCase = true))
            require(!uri.host.isNullOrBlank() && uri.rawUserInfo == null)
            require(uri.port in -1..65535 && uri.port != 0)
            require(uri.rawQuery == null && uri.rawFragment == null)
            require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")
            return URI("https", null, uri.host, uri.port, null, null, null).toASCIIString()
        }
    }
}
