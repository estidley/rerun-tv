package com.rerun.tv.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * App settings. Plex tokens live in encrypted preferences when the device supports it;
 * everything else is a plain key/value row in Room.
 */
class Settings(context: Context, private val dao: SettingDao) {
    private val secure: SharedPreferences = try {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "rerun_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        context.getSharedPreferences("rerun_secure_fallback", Context.MODE_PRIVATE)
    }

    val clientId: String
        get() = secure.getString(CLIENT_ID, null) ?: UUID.randomUUID().toString().also {
            secure.edit().putString(CLIENT_ID, it).apply()
        }

    var accountToken: String?
        get() = secure.getString(ACCOUNT_TOKEN, null)
        set(v) = secure.edit().putString(ACCOUNT_TOKEN, v).apply()

    var serverToken: String?
        get() = secure.getString(SERVER_TOKEN, null)
        set(v) = secure.edit().putString(SERVER_TOKEN, v).apply()

    suspend fun serverBase(): String? = dao.get(SERVER_BASE)
    suspend fun setServerBase(v: String) = dao.put(SettingRow(SERVER_BASE, v))
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

    suspend fun signOut() {
        secure.edit().remove(ACCOUNT_TOKEN).remove(SERVER_TOKEN).apply()
        listOf(SERVER_BASE, SERVER_NAME, SERVER_ID, LAST_SYNC).forEach { dao.remove(it) }
    }

    val isSignedIn: Boolean get() = accountToken != null

    private companion object {
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
