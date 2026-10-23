// Minimal stand-in for kotlin.test so :core tests compile and run without Gradle/Maven.
// Used only by tools/ktest/run.sh. On a normal machine, Gradle uses the real kotlin-test.
@file:Suppress("unused")

package kotlin.test

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Test

class AssertionFailed(message: String) : AssertionError(message)

fun fail(message: String? = null): Nothing = throw AssertionFailed(message ?: "failed")

fun <T> assertEquals(expected: T, actual: T, message: String? = null) {
    if (expected != actual) fail((message?.let { "$it: " } ?: "") + "expected <$expected> but was <$actual>")
}

fun <T> assertNotEquals(illegal: T, actual: T, message: String? = null) {
    if (illegal == actual) fail((message?.let { "$it: " } ?: "") + "unexpected <$actual>")
}

fun assertTrue(actual: Boolean, message: String? = null) {
    if (!actual) fail(message ?: "expected true")
}

fun assertFalse(actual: Boolean, message: String? = null) {
    if (actual) fail(message ?: "expected false")
}

fun assertNull(actual: Any?, message: String? = null) {
    if (actual != null) fail((message?.let { "$it: " } ?: "") + "expected null but was <$actual>")
}

fun <T : Any> assertNotNull(actual: T?, message: String? = null): T {
    if (actual == null) fail(message ?: "expected non-null")
    return actual
}

inline fun <reified T : Throwable> assertFailsWith(message: String? = null, block: () -> Unit): T {
    try {
        block()
    } catch (e: Throwable) {
        if (e is T) return e
        fail((message?.let { "$it: " } ?: "") + "expected ${T::class.java.name} but got $e")
    }
    fail((message?.let { "$it: " } ?: "") + "expected ${T::class.java.name} but nothing was thrown")
}
