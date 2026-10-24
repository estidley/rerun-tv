package com.rerun.tv.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.rerun.core.plex.ServerUri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.UUID

/**
 * App settings. Plex tokens live in encrypted preferences. If the keystore cannot
 * be used, tokens stay in memory for this process and are never written in plaintext.
 * The client id is not a secret and lives in ordinary preferences.
 */
class Settings(context: Context, private val dao: SettingDao) {
    private val device = context.getSharedPreferences(DEVICE_PREFS, Context.MODE_PRIVATE)
    private val secure: SharedPreferences? = openEncrypted(context)
    private val memory = HashMap<String, String?>()

    @Volatile private var pinnedHost: String? = null

    init {
        retirePlaintextTokens(context)
        secure?.getString(CLIENT_ID, null)?.let { saved ->
            if (device.getString(CLIENT_ID, null) == null) device.edit().putString(CLIENT_ID, saved).apply()
        }
    }

    /** Host of the server the user picked. Playback and images attach the token only here, plus Plex's own names and the LAN. */
    fun pinnedHost(): String? = pinnedHost

    val clientId: String
        get() = device.getString(CLIENT_ID, null) ?: UUID.randomUUID().toString().also {
            device.edit().putString(CLIENT_ID, it).apply()
        }

    var accountToken: String?
        get() = secret(ACCOUNT_TOKEN)
        set(v) = putSecret(ACCOUNT_TOKEN, v)

    var serverToken: String?
        get() = secret(SERVER_TOKEN)
        set(v) = putSecret(SERVER_TOKEN, v)

    suspend fun serverBase(): String? = dao.get(SERVER_BASE).also { updatePin(it) }
    suspend fun setServerBase(v: String) {
        updatePin(v)
        dao.put(SettingRow(SERVER_BASE, v))
    }
    suspend fun serverName(): String? = dao.get(SERVER_NAME)
    suspend fun setServerName(v: String) = dao.put(SettingRow(SERVER_NAME, v))
    suspend fun serverId(): String? = dao.get(SERVER_ID)
    suspend fun setServerId(v: String) = dao.put(SettingRow(SERVER_ID, v))

    suspend fun lastSyncMs(): Long = dao.get(LAST_SYNC)?.toLongOrNull() ?: 0
    suspend fun setLastSyncMs(v: Long) = dao.put(SettingRow(LAST_SYNC, v.toString()))

    suspend fun lastChannel(): String? = dao.get(LAST_CHANNEL)
    suspend fun setLastChannel(v: String) = dao.put(SettingRow(LAST_CHANNEL, v))

    fun crtLevel(): Flow<String> = dao.observe(CRT_LEVEL).map { it ?: "SUBTLE" }
    suspend fun setCrtLevel(v: String) = dao.put(SettingRow(CRT_LEVEL, v))

    fun flag(key: String, default: Boolean = true): Flow<Boolean> = dao.observe(key).map { it?.toBooleanStrictOrNull() ?: default }
    suspend fun setFlag(key: String, on: Boolean) = dao.put(SettingRow(key, on.toString()))

    /** One-shot read. [flag] defaults to on for effects; lineup switches default off. */
    suspend fun isOn(key: String, default: Boolean = false): Boolean =
        dao.get(key)?.toBooleanStrictOrNull() ?: default

    suspend fun signOut() {
        putSecret(ACCOUNT_TOKEN, null)
        putSecret(SERVER_TOKEN, null)
        pinnedHost = null
        listOf(SERVER_BASE, SERVER_NAME, SERVER_ID, LAST_SYNC).forEach { dao.remove(it) }
    }

    val isSignedIn: Boolean get() = accountToken != null

    private fun updatePin(base: String?) {
        pinnedHost = base?.let { ServerUri.hostOf(it) }
    }

    private fun secret(key: String): String? = secure?.getString(key, null) ?: memory[key]

    private fun putSecret(key: String, value: String?) {
        val store = secure
        if (store != null) {
            val edit = store.edit()
            if (value == null) edit.remove(key) else edit.putString(key, value)
            edit.apply()
        } else if (value == null) {
            memory.remove(key)
        } else {
            memory[key] = value
        }
    }

    /** Keystore failures on some Fire TV devices are a grab bag. Tokens then stay in memory. */
    @Suppress("DEPRECATION")
    private fun openEncrypted(context: Context): SharedPreferences? = try {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, SECURE_PREFS, key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (_: GeneralSecurityException) {
        null
    } catch (_: IOException) {
        null
    } catch (_: Exception) {
        null
    }

    /**
     * Older builds stored tokens in plaintext when the keystore failed.
     * Move them into the encrypted store, or into memory, and delete the file.
     */
    private fun retirePlaintextTokens(context: Context) {
        val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        if (legacy.all.isEmpty()) return
        legacy.getString(CLIENT_ID, null)?.let { saved ->
            if (device.getString(CLIENT_ID, null) == null) device.edit().putString(CLIENT_ID, saved).apply()
        }
        for (key in listOf(ACCOUNT_TOKEN, SERVER_TOKEN)) {
            val value = legacy.getString(key, null) ?: continue
            if (secret(key) == null) putSecret(key, value)
        }
        legacy.edit().clear().commit()
        context.deleteSharedPreferences(LEGACY_PREFS)
    }

    companion object {
        const val EAST_WEST = "opt_east_west"
        const val MUSIC = "opt_music"
        const val SUBTITLES = "opt_subtitles"
        const val DEVICE_PREFS = "rerun_device"
        const val SECURE_PREFS = "rerun_secure"
        const val LEGACY_PREFS = "rerun_secure_fallback"
        const val CLIENT_ID = "client_id"
        const val ACCOUNT_TOKEN = "account_token"
        const val SERVER_TOKEN = "server_token"
        const val SERVER_BASE = "server_base"
        const val SERVER_NAME = "server_name"
        const val SERVER_ID = "server_id"
        const val LAST_SYNC = "last_sync"
        const val LAST_CHANNEL = "last_channel"
        const val CRT_LEVEL = "crt_level"
    }
}
