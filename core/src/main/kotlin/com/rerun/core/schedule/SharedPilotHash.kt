package com.rerun.core.schedule

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/** Every field, including the domain, is length prefixed; numeric fields are big endian. */
internal fun pilotHash(domain: String, vararg fields: ByteArray): ByteArray {
    val bytes = ByteArrayOutputStream()
    DataOutputStream(bytes).use { output ->
        for (field in listOf(domain.toByteArray(Charsets.UTF_8)) + fields) {
            output.writeInt(field.size)
            output.write(field)
        }
    }
    return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
}

internal fun pilotBytes(value: String): ByteArray = value.toByteArray(Charsets.UTF_8)

internal val pilotByteOrder = Comparator<ByteArray> { left, right ->
    var result = 0
    for (i in 0 until minOf(left.size, right.size)) {
        result = (left[i].toInt() and 255).compareTo(right[i].toInt() and 255)
        if (result != 0) break
    }
    if (result == 0) left.size.compareTo(right.size) else result
}
