package com.rerun.core.schedule

import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Locale

/**
 * Fixed UTC v1 pilot recipe. The 24 Crockford symbols encode a version byte, signed 64-bit
 * seed, signed 32-bit UTC epoch day, and a 16-bit SHA-256 checksum (not authentication).
 * No server identity, credentials, or library data are included.
 */
data class SharedScheduleCode(val seed: Long, val anchorDay: Long) {
    init {
        require(anchorDay in -MAX_DAY..MAX_DAY) { "Shared schedule anchor day is outside the supported range" }
    }

    fun encode(): String {
        val payload = ByteBuffer.allocate(13).put(1).putLong(seed).putInt(anchorDay.toInt()).array()
        val check = pilotHash("code-v1", payload)
        val bytes = payload + check.copyOf(2)
        return buildString {
            var bits = 0
            var value = 0
            for (byte in bytes) {
                value = (value shl 8) or (byte.toInt() and 255)
                bits += 8
                while (bits >= 5) {
                    bits -= 5
                    append(ALPHABET[(value ushr bits) and 31])
                }
            }
        }
    }

    companion object {
        internal const val DAY_MS = 86_400_000L
        internal const val MAX_DAY = 100_000_000L
        private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

        fun generate(nowMs: Long): SharedScheduleCode =
            SharedScheduleCode(SecureRandom().nextLong(), Math.floorDiv(nowMs, DAY_MS))

        fun decode(text: String): SharedScheduleCode {
            require(text.length <= 128) { "Shared schedule code is too long" }
            val symbols = text.trim().replace("-", "").uppercase(Locale.ROOT)
            require(symbols.length == 24) { "Shared schedule code must contain 24 symbols" }
            val bytes = ByteArray(15)
            var value = 0
            var bits = 0
            var index = 0
            for (raw in symbols) {
                val char = when (raw) { 'O' -> '0'; 'I', 'L' -> '1'; else -> raw }
                val digit = ALPHABET.indexOf(char)
                require(digit >= 0) { "Invalid shared schedule code symbol: $raw" }
                value = (value shl 5) or digit
                bits += 5
                if (bits >= 8) {
                    bits -= 8
                    bytes[index++] = (value ushr bits).toByte()
                }
            }
            val payload = bytes.copyOf(13)
            require(pilotHash("code-v1", payload).copyOf(2).contentEquals(bytes.copyOfRange(13, 15))) {
                "Shared schedule code checksum does not match; check for a typing error"
            }
            val buffer = ByteBuffer.wrap(payload)
            require(buffer.get().toInt() == 1) { "Unsupported shared schedule code version" }
            return SharedScheduleCode(buffer.long, buffer.int.toLong())
        }
    }
}
